rm -f database-dump.html
java -jar schemacrawler-4.0.jar -c hsqldb -command=count,dump -outputformat=html -outputfile=database-dump.html
echo Database dump is in database-dump.html