FROM java:8-jre
MAINTAINER RenFei <i@renfei.net>

ADD ./target/WinterEE-Gateway-Serve.jar /app/
CMD ["java","-Xms128M","-Xmx512M","-XX:+UseCompressedOops","-XX:+UseConcMarkSweepGC","-XX:SoftRefLRUPolicyMSPerMB=50","-Dfile.encoding=UTF-8","-Xverify:none","-Ddruid.mysql.usePingMethod=false","-jar","/app/WinterEE-Gateway-Serve.jar"]

EXPOSE 8081