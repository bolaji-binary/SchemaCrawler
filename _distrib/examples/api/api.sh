rm -f *.class
javac -classpath schemacrawler-3.9.jar ApiExample.java
java -classpath .:schemacrawler-3.9.jar ApiExample
