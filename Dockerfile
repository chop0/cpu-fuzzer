FROM openjdk:22-bookworm

RUN wget https://download.java.net/java/early_access/jextract/22/5/openjdk-22-jextract+5-33_linux-x64_bin.tar.gz -O /tmp/jextract.tar.gz
RUN tar -xzf /tmp/jextract.tar.gz -C /tmp
RUN mkdir -p /usr/lib/jvm
RUN mv /tmp/jextract-* /usr/lib/jvm/jextract

ENV PATH="/usr/lib/jvm/jextract/bin:${PATH}"

RUN apt-get update && apt-get install -y build-essential
WORKDIR /app
COPY . .
RUN make ax.xz.fuzz libslave.so
ENTRYPOINT make run