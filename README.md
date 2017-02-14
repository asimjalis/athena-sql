# Athena SQL

## What

Command line tool to query AWS Athena using SQL. 

## Steps

### Install AWSCLI

    pip install --upgrade awscli
    
### Configure Credentials

Configure `~/.aws/credentials` with your AWS Access Key ID and Secret Access Key.

### Install Athena Driver

Download Athena driver.

    aws s3 cp s3://athena-downloads/drivers/AthenaJDBC41-1.0.0.jar .
    
Make sure you have maven installed. 

Install Athena driver into local Maven repo.

    mvn install:install-file \
      -Dfile=AthenaJDBC41-1.0.0.jar \
      -DgroupId=com.amazonaws \
      -DartifactId=athena-jdbc \
      -Dversion=1.0.0 \
      -Dpackaging=jar

### Download Athena-SQL

    git clone https://github.com/asimjalis/athena-sql
    cd athena-sql
    export ATHENA_HOME=$(pwd)
    export PATH=$PATH:$ATHENA_HOME/bin
    cd

### Configure Athena-SQL

Create S3 staging directory.

    aws s3 mb s3://asimj-athena-s3-staging-dir

Edit `config.edn` to specify an S3 staging directory that you have write access to.

    {:default 
     {:s3-staging-dir "s3://asimj-athena-s3-staging-dir"
      :log-path "/tmp/athena.log"
      :output :csv } }


You can configure the output format to be CSV, JSON, or EDN. 

The default is CSV.

### Run

    athena-sql "DESCRIBE DATABASES"
    athena-sql "DESCRIBE TABLES IN default"

## Demo

### Create Sales Data

    #ID,Date,Store,State,Product,Amount
    cat << END > sales.csv
    101,2014-11-13,100,WA,331,300.00
    104,2014-11-18,700,OR,329,450.00
    102,2014-11-15,203,CA,321,200.00
    106,2014-11-19,202,CA,331,330.00
    103,2014-11-17,101,WA,373,750.00
    105,2014-11-19,202,CA,321,200.00
    END

### Make Bucket

Replace this bucket name with your own unique bucket name.

    aws s3 mb s3://asimj-athena-example

### Stage Data 

    aws s3 cp sales.csv s3://asimj-athena-example/data/sales.csv

### Verify Data

    aws s3 cp s3://asimj-athena-example/data/sales.csv -

### Create Table

    athena-sql "
      CREATE EXTERNAL TABLE IF NOT EXISTS sales (
        id INT,
        date DATE,
        store INT,
        state STRING,
        product INT,
        amount DOUBLE) 
      ROW FORMAT DELIMITED
      FIELDS TERMINATED BY ','
      STORED AS TEXTFILE
      LOCATION 's3://asimj-athena-example/data/'
    "

    athena-sql "SHOW TABLES IN DEFAULT"
    athena-sql "SELECT * FROM SALES LIMIT 10"

### Query

    athena-sql "
      SELECT state, COUNT(*) AS count 
      FROM sales GROUP BY state ORDER BY count desc
    "
    
### Drop Table

    athena-sql "DROP TABLE sales"

## Troubleshooting

In `conf/log4j.properties` replace `OFF` with `ERROR` or `WARN` to get full stack trace.
