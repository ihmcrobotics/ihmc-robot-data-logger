
mkdir -p ~/robotLogs

# Creating /opt/ihmc
sudo mkdir -p /opt/ihmc

# Unpacking ${DIST} to /opt/ihmc
sudo tar xf ${DIST} -C /opt/ihmc

# Removing old distribution
sudo  rm -rf /opt/ihmc/logger

# Moving new distribution in place
sudo  mv /opt/ihmc/${DIST_NAME} /opt/ihmc/logger

# The logger service runs as ${USER}, but everything above ran as root, so hand
# the install back to the runtime user regardless of what permissions the
# packaged files arrived with.
sudo chown -R ${USER}:${USER} /opt/ihmc/logger

# Marking logger start scripts executable
sudo /bin/chmod a+x /opt/ihmc/logger/bin

# Setting up nightly restart
if ${NIGHTLY_RESTART}; then sudo  cp ${CRON_ENTRY} /etc/cron.d/ihmc-logger-cron && echo "Restarting logger at midnight every night."; else sudo rm -f /etc/cron.d/ihmc-logger-cron && echo "Removed automatic restart"; fi
rm -f ${CRON_ENTRY}

# The unit file is always installed, so pick it up so it shows up in systemctl
sudo /bin/systemctl daemon-reload && echo "Reloaded systemctl"

# Only enable/start the service if the checkbox asked for it
if ${DEPLOY_SERVICE}; then sudo /bin/systemctl enable ihmc-logger.service && echo "Enabled ihmc-logger.service"; fi
if ${DEPLOY_SERVICE}; then sudo /bin/systemctl restart ihmc-logger.service && echo "Restarted ihmc-logger.service"; fi

# Restarting cron
sudo service cron restart && echo "Restarted cron"

sync