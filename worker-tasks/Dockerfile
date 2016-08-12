FROM quay.io/azavea/spark:1.6.2

USER root

RUN \
      mkdir -p /spark-state \
      && chown spark:spark -R /spark-state

USER spark
