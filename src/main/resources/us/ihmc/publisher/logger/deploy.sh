
mkdir -p ~/robotLogs

# Creating /opt/ihmc
sudo mkdir -p /opt/ihmc

# Unpacking ${DIST} to /opt/ihmc
sudo tar xf ${DIST} -C /opt/ihmc

# Removing old distribution
sudo  rm -rf /opt/ihmc/logger

# Moving new distribution in place
sudo  mv /opt/ihmc/${DIST_NAME} /opt/ihmc/logger

# Marking logger start scripts executable
sudo /bin/chmod a+x /opt/ihmc/logger/bin

# Setting up nightly restart
if ${NIGHTLY_RESTART}; then sudo  cp ${CRON_ENTRY} /etc/cron.d/ihmc-logger-cron && echo "Restarting logger at midnight every night."; else sudo rm -f /etc/cron.d/ihmc-logger-cron && echo "Removed automatic restart"; fi
rm -f ${CRON_ENTRY}


# Reloading systemd
sudo /bin/systemctl daemon-reload && echo "Reloaded systemctl"
sudo /bin/systemctl enable ihmc-logger.service && echo "Enabled ihmc-logger.service"

# Deploying service
if ${DEPLOY_SERVICE}; then sudo /bin/systemctl restart ihmc-logger.service && echo "Restarted ihmc-logger.service"; fi

# Restarting cron
sudo service cron restart && echo "Restarted cron"

sync