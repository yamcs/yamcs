FROM yamcs/yamcs:3.0.0

COPY tcp.yaml etc/tcp.yaml
COPY yamcs.yaml /opt/yamcs/etc/yamcs.yaml
RUN apt-get update && apt-get install -y vim 

COPY entry_point.sh /
ENTRYPOINT ["/entry_point.sh"]

CMD ["yss"]
