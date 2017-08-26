FROM ysihaoy/scala-play

# Cache dependencies
COPY ["build.sbt", "/tmp/build/"]
COPY ["project/plugins.sbt", "project/build.properties", "/tmp/build/project/"]
RUN cd /tmp/build && \
  activator compile && \
  activator test:compile && \
  rm -rf /tmp/build

# Copy source code
COPY . /root/app/
WORKDIR /root/app
RUN activator compile && activator test:compile

EXPOSE 9000
CMD ["activator", "run"]
