
mkdir -p ~/robotLogs

# Creating /opt/ihmc
@echo ${SUDO_PW} | sudo -S  mkdir -p /opt/ihmc

# Unpacking ${DIST} to /opt/ihmc
@echo ${SUDO_PW} | sudo -S tar xf ${DIST} -C /opt/ihmc

# Removing old distribution
@echo ${SUDO_PW} | sudo -S  rm -rf /opt/ihmc/logger

# Moving new distribution in place
@echo ${SUDO_PW} | sudo -S  mv /opt/ihmc/${DIST_NAME} /opt/ihmc/logger
rm -f ${DIST}

# Marking logger start scripts executable
@echo ${SUDO_PW} | sudo -S  /bin/chmod a+x /opt/ihmc/logger/bin

# Installing systemctl service
@echo ${SUDO_PW} | sudo -S  mv ${LOGGER_SERVICE} /etc/systemd/system/ihmc-logger.service && echo "Installed ihmc-logger.serivce"

# Setting up nightly restart 
@if ${NIGHTLY_RESTART}; then echo ${SUDO_PW} | sudo -S  cp ${CRON_ENTRY} /etc/cron.d/ihmc-logger-cron && echo "Restarting logger at midnight every night."; else  echo ${SUDO_PW} | sudo -S rm -f /etc/cron.d/ihmc-logger-cron && echo "Removed automatic restart"; fi
rm -f ${CRON_ENTRY}


# Reloading systemd 
@echo ${SUDO_PW} | sudo -S /bin/systemctl daemon-reload && echo "Reloaded systemctl"
@echo ${SUDO_PW} | sudo -S /bin/systemctl enable ihmc-logger.service && echo "Enabled ihmc-logger.service"
@echo ${SUDO_PW} | sudo -S /bin/systemctl restart ihmc-logger.service && echo "Restarted ihmc-logger.service"

# Restarting cron
@echo ${SUDO_PW} | sudo -S service cron restart && echo "Restarted cron"


