# Athena SQL Demo

### Create Sales Data

    cd data
    cat sales.csv

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

### States With Highest Transaction Count

    athena-sql "
      SELECT state, COUNT(*) AS count 
      FROM sales GROUP BY state ORDER BY count DESC
    "

### States With Highest Revenue

    athena-sql "
      SELECT state, SUM(amount) AS revenue
      FROM sales GROUP BY state ORDER BY revenue DESC
    "

### Drop Table

    athena-sql "DROP TABLE sales"

