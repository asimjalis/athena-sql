#!/usr/bin/env boot

; Query Athena using JDBC

; Dependencies
(defn dep-add [dep ver]  (merge-env! :dependencies [[dep ver]]))
(defn classpath-add [cp] (merge-env! :resource-paths [cp]))

; Athena Home
(defn base-name [path] (-> path (.replaceAll ".*/(.*)" "$1")))
(defn dir-name [path] (-> path (.replaceAll "(.*)/.*" "$1")))
(def athena-script 
  (or *boot-script* 
      (-> "HOME" 
          (System/getenv) 
          (str "/g/athena-sql/bin/athena-sql.clj"))))
(def athena-script-name (-> athena-script base-name))
(def athena-home (-> athena-script dir-name))
(def athena-conf (str athena-home "/../conf"))

; Add classpath for log4j.properties
(classpath-add athena-conf)

; Add all dependencies here
(reset! *verbosity* 0)
(dep-add 'lein-cprint "1.2.0") 
(dep-add 'com.amazonaws/athena-jdbc "1.0.0")
(dep-add 'com.amazonaws/aws-java-sdk-core "1.11.86")
(dep-add 'com.amazonaws/aws-java-sdk-iam "1.11.86")
(dep-add 'org.clojure/data.json "0.2.6")
(dep-add 'org.clojure/data.csv "0.1.3")
(dep-add 'org.clojure/java.jdbc "0.7.0-alpha1")
(reset! *verbosity* 1)

; [Make REPL prettier using colors]
(require '[leiningen.cprint :refer [cprint]])

; [Reload]
(defn reload-me []
  (->> "CLJ_TMP" (System/getenv) (load-file)))
(defn rr [] (reload-me))

; [Error]
(require '[clojure.string :as str])
(defn error [& msgs]
  (throw (new Error (apply str msgs))))

; [Config]

(require 'clojure.edn)

(defn inject-default [expr default]
  (if expr expr default))

(defn verify-required [param]
  (if param param (error (str (name param) " required"))))

(defn make-config []
  (let [file   (str athena-conf "/config.edn")
        config (-> file (slurp) (clojure.edn/read-string) :default)]
    {:s3-staging-dir (-> config :s3-staging-dir (verify-required))
     :log-path       (-> config :log-path       (verify-required))
     :output         (-> config :output         (inject-default :edn))}))

; [AWS Info]

(import com.amazonaws.services.identitymanagement.AmazonIdentityManagementClient)
(import com.amazonaws.auth.DefaultAWSCredentialsProviderChain)
(import com.amazonaws.regions.DefaultAwsRegionProviderChain)

(defn make-aws-info [] 
  {:aws-user          (->> (new AmazonIdentityManagementClient) 
                           .getUser .getUser .getUserName)
   :aws-region        (->> (new DefaultAwsRegionProviderChain) 
                           .getRegion)
   :aws-access-key-id (->> (DefaultAWSCredentialsProviderChain/getInstance) 
                           .getCredentials 
                           .getAWSAccessKeyId)
   :aws-secret-key    (->> (DefaultAWSCredentialsProviderChain/getInstance) 
                           .getCredentials
                           .getAWSSecretKey)})

(import java.util.Properties)
(import java.sql.DriverManager)

(defn make-stmt [config aws-info]
  (let [s3-staging-dir (-> config :s3-staging-dir)
        aws-region (-> aws-info :aws-region)
        aws-access-key-id (-> aws-info :aws-access-key-id)
        aws-secret-key (-> aws-info :aws-secret-key)
        athena-host (str "athena." aws-region ".amazonaws.com")
        athena-port "443"
        athena-uri (str "jdbc:awsathena://" athena-host ":" athena-port)
        athena-info (doto (new Properties)
                      (.put "s3_staging_dir" s3-staging-dir)
                      (.put "log_path" "/tmp/athena.log")
                      (.put "user" aws-access-key-id)
                      (.put "password" aws-secret-key))
        athena-conn (DriverManager/getConnection 
                      athena-uri
                      athena-info)
        athena-stmt (.createStatement athena-conn)]
    athena-stmt))

(def ^:dynamic *athena-stmt*)

(defn sql-is-create [sql]
  (-> sql 
      (.replaceAll "\n" " ") 
      (.toLowerCase) 
      (.matches " *(create|drop|alter|rename) .*")))

(require 'clojure.java.jdbc)

(defn sql->seq [sql]
  (->> sql 
       (.executeQuery *athena-stmt*) 
       clojure.java.jdbc/result-set-seq))

; [Usage]

(def USAGE
  (str
    "Usage: " athena-script-name " SQL"))

; [Output]

(require '[clojure.data.json :as json])
(require '[clojure.data.csv :as csv])

(defn csv->out  [s] (->> s (csv/write-csv *out*)))
(defn seq->json [s] (->> s json/pprint))
(defn seq->edn  [s] (->> s cprint))
(defn seq->csv  "Prints CSV data without header"
  [s] (->> s (map vals) csv->out))

(defn sql->json [sql] (->> sql sql->seq seq->json))
(defn sql->edn  [sql] (->> sql sql->seq seq->edn))
(defn sql->csv  [sql] (->> sql sql->seq seq->csv))

; [Test]

(defn test-all []
  (->> "SELECT * FROM sampledb.elb_logs LIMIT 2" sql->json)
  (->> "SELECT * FROM sampledb.elb_logs LIMIT 2" sql->csv)
  (->> "SHOW TABLES IN DEFAULT" sql->edn)
  (->> "SELECT COUNT(*) AS record_count FROM elb_logs" sql->edn)
  (->> "SELECT * FROM sampledb.elb_logs LIMIT 2" sql->edn))

;[Main Dispatch]

(defn output->action [sql output]
  (cond 
    (= output :edn)  (-> sql sql->edn)
    (= output :json) (-> sql sql->json)
    :else            (-> sql sql->csv)))

(defn -main [& args]
  (try 
    (if (-> args (count) (not= 1)) 
      (println USAGE)
      (let [sql (nth args 0)
            config (make-config)
            aws-info (make-aws-info)
            athena-stmt (make-stmt config aws-info)
            output (-> config :output)]
          (binding [*athena-stmt* athena-stmt]
            (output->action sql output))))
    (catch Exception e
      (println "Error:" (.getMessage e)))))
