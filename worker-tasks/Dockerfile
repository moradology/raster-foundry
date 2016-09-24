FROM quay.io/azavea/spark:2.0.0

USER root

RUN \
      mkdir -p /spark-state \
      && chown spark:spark -R /spark-state

USER spark
