package us.ihmc.publisher.logger.ui;

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.ResourceBundle;
import java.util.prefs.Preferences;

import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.Alert;
import javafx.scene.control.Alert.AlertType;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.SelectionMode;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.control.cell.TextFieldTableCell;
import javafx.stage.FileChooser;
import javafx.stage.Stage;
import javafx.util.converter.IntegerStringConverter;
import us.ihmc.publisher.logger.ui.HostBean.CameraHolder;
import us.ihmc.publisher.logger.utils.ui.PreferencesHolder;
import us.ihmc.robotDataLogger.CameraConfiguration;
import us.ihmc.robotDataLogger.CameraSettings;
import us.ihmc.robotDataLogger.CameraType;
import us.ihmc.robotDataLogger.Host;
import us.ihmc.robotDataLogger.StaticHostList;

public class LoggerDeployController implements Initializable
{
   private LoggerDeployScript loggerDeployScript;

   PreferencesHolder prefs;

   @FXML
   TextField logger_host;

   @FXML
   TextField logger_user;

   @FXML
   PasswordField logger_pasword;

   @FXML
   PasswordField logger_sudo_password;

   ObservableList<CameraBean> cameraList = FXCollections.observableArrayList();

   @FXML
   TableView<CameraBean> camera_table;

   @FXML
   TableColumn<CameraBean, String> camera_name_col;

   @FXML
   TableColumn<CameraBean, Integer> camera_id_col;

   @FXML
   TableColumn<CameraBean, Integer> camera_input_col;

   ObservableList<HostBean> hostList = FXCollections.observableArrayList();

   @FXML
   TableView<HostBean> host_table;

   @FXML
   TableColumn<HostBean, String> host_col;

   @FXML
   TableColumn<HostBean, Integer> port_col;

   @FXML
   TableColumn<HostBean, CameraHolder> host_cameras_col;

   @FXML
   TextField logger_dist;

   @FXML
   Button browse_dist;

   @FXML
   CheckBox logger_restart_midnight;

   @FXML
   Label restart_label;
   
   @FXML
   CheckBox restart_on_save;

   @FXML
   CheckBox logger_service;
   

   @Override
   public void initialize(URL location, ResourceBundle resources)
   {
      prefs = new PreferencesHolder(Preferences.userRoot().node(this.getClass().getSimpleName()));

      prefs.linkToPrefs(logger_host, "127.0.0.1");
      prefs.linkToPrefs(logger_user, "halodi");
      prefs.linkToPrefs(logger_pasword, "halodi");
      prefs.linkToPrefs(logger_sudo_password, "halodi");

      prefs.linkToPrefs(logger_dist, "");

      prefs.linkToPrefs(logger_restart_midnight, false);
      
      prefs.linkToPrefs(restart_on_save, true);

      prefs.linkToPrefs(logger_service, true);

      camera_table.setEditable(true);

      camera_id_col.setCellValueFactory(new PropertyValueFactory<CameraBean, Integer>("camera_id"));

      camera_name_col.setCellFactory(TextFieldTableCell.forTableColumn());
      camera_name_col.setCellValueFactory(new PropertyValueFactory<CameraBean, String>("camera_name"));
      camera_name_col.setOnEditCommit(e ->
      {
         e.getRowValue().camera_name.set(e.getNewValue());
      });

      camera_input_col.setCellFactory(TextFieldTableCell.forTableColumn(new IntegerStringConverter()));
      camera_input_col.setCellValueFactory(new PropertyValueFactory<CameraBean, Integer>("camera_input"));
      camera_input_col.setOnEditCommit(e ->
      {
         e.getRowValue().camera_input.set(e.getNewValue());
      });

      camera_table.setItems(cameraList);
      camera_table.getSelectionModel().setSelectionMode(SelectionMode.MULTIPLE);

      host_table.setEditable(true);
      host_col.setCellFactory(TextFieldTableCell.forTableColumn());
      host_col.setCellValueFactory(new PropertyValueFactory<HostBean, String>("hostname"));
      host_col.setOnEditCommit(e ->
      {
         e.getRowValue().hostname.set(e.getNewValue());
      });

      port_col.setCellFactory(TextFieldTableCell.forTableColumn(new IntegerStringConverter()));
      port_col.setCellValueFactory(new PropertyValueFactory<HostBean, Integer>("port"));
      port_col.setOnEditCommit(e ->
      {
         e.getRowValue().port.set(e.getNewValue());
      });

      host_cameras_col.setCellValueFactory(new PropertyValueFactory<HostBean, CameraHolder>("cameras"));

      host_table.setItems(hostList);
   }

   /**
    * Set the script to deploy the logger. This allows writing custom deploy applications for different environments
    * 
    * @param loggerDeployScript
    */
   public void setDeployScript(LoggerDeployScript loggerDeployScript)
   {
      this.loggerDeployScript = loggerDeployScript;

      if (!loggerDeployScript.implementsAutoRestart())
      {
         restart_label.setVisible(false);
         logger_restart_midnight.setVisible(false);
         logger_service.setVisible(false);
      }
   }

   private byte getNextFreeCameraId()
   {
      for (byte i = 0; i < 128; i++)
      {
         boolean isFree = true;

         for (CameraBean camera : cameraList)
         {
            if (camera.getCamera_id() == i)
            {
               isFree = false;
            }
         }

         if (isFree)
         {
            return i;
         }
      }

      return -1;
   }

   @FXML
   void camera_add(ActionEvent e)
   {
      byte nextId = getNextFreeCameraId();

      if (nextId < 0)
      {
         Alert alert = new Alert(AlertType.ERROR);
         alert.setTitle("Too many cameras");
         alert.setHeaderText("No more than 128 cameras are supported");
         alert.showAndWait();
      }
      else
      {
         cameraList.add(new CameraBean(nextId));
      }
   }

   @FXML
   void camera_remove(ActionEvent e)
   {
      ArrayList<CameraBean> toRemove = new ArrayList<>(camera_table.getSelectionModel().getSelectedItems());

      for (CameraBean camera : toRemove)
      {
         cameraList.remove(camera);
      }

   }

   @FXML
   void host_add(ActionEvent e)
   {
      hostList.add(new HostBean());
   }

   @FXML
   void host_remove(ActionEvent e)
   {
      hostList.remove(host_table.getSelectionModel().getSelectedItem());
   }

   @FXML
   void set_cameras(ActionEvent e)
   {
      List<CameraBean> cameras = camera_table.getSelectionModel().getSelectedItems();

      host_table.getSelectionModel().getSelectedItem().setCameras(new CameraHolder(cameras));

      host_table.refresh();
   }

   @FXML
   void load(ActionEvent e)
   {
      CameraSettings settings;

      try
      {
         settings = loggerDeployScript.loadCameraConfiguration(logger_host.getText(), logger_user.getText(), logger_pasword.getText(), logger_sudo_password.getText(), getStage());
      }
      catch (IOException ex)
      {
         Alert alert = new Alert(AlertType.ERROR);
         alert.setTitle("Cannot load camera configuration.");
         alert.setHeaderText("Cannot load camera configuration from host. Initializing to empty configuration");
         alert.setContentText(ex.getMessage());
         alert.showAndWait();
         settings = new CameraSettings();
      }

      StaticHostList hosts;
      try
      {
         hosts = loggerDeployScript.loadStaticHostList(logger_host.getText(), logger_user.getText(), logger_pasword.getText(), logger_sudo_password.getText(), getStage());
      }
      catch (IOException ex)
      {
         Alert alert = new Alert(AlertType.ERROR);
         alert.setTitle("Cannot load host configuration.");
         alert.setHeaderText("Cannot load host configuration from host. Initializing to empty host list");
         alert.setContentText(ex.getMessage());
         alert.showAndWait();
         hosts = new StaticHostList();
      }

      try
      {
         cameraList.clear();
         hostList.clear();

         if (settings != null && settings.getCameras() != null)
         {

            for (CameraConfiguration config : settings.getCameras())
            {
               if (config.getType() == CameraType.CAPTURE_CARD)
               {
                  CameraBean bean = new CameraBean(config);
                  cameraList.add(bean);
               }
            }
         }

         if (hosts != null && hosts.getHosts() != null)
         {

            for (Host host : hosts.getHosts())
            {
               HostBean bean = new HostBean(host);
               hostList.add(bean);
            }
         }

         camera_table.refresh();
         host_table.refresh();
      }
      catch (NumberFormatException ex)
      {
         Alert alert = new Alert(AlertType.ERROR);
         alert.setTitle("Configuration is corrupted.");
         alert.setHeaderText(ex.getMessage());
         alert.showAndWait();
      }
   }

   @FXML
   void save(ActionEvent e)
   {
      CameraSettings settings = new CameraSettings();

      for (CameraBean cameraBean : cameraList)
      {
         CameraConfiguration config = settings.getCameras().add();
         cameraBean.pack(config);
      }

      StaticHostList staticHosts = new StaticHostList();
      for (HostBean hostBean : hostList)
      {
         Host host = staticHosts.getHosts().add();
         hostBean.pack(host);

      }

      loggerDeployScript.saveConfiguration(logger_host.getText(), logger_user.getText(), logger_pasword.getText(), logger_sudo_password.getText(), settings, staticHosts, restart_on_save.isSelected(), getStage());
   }

   @FXML
   void logger_deploy(ActionEvent e)
   {
         loggerDeployScript.deploy(logger_host.getText(),
                                   logger_user.getText(),
                                   logger_pasword.getText(),
                                   logger_sudo_password.getText(),
                                   logger_dist.getText(),
                                   logger_restart_midnight.isSelected(),
                                   getStage(),
                                   logger_service.isSelected());
   }

   private Stage getStage()
   {
      return (Stage) logger_host.getScene().getWindow();
   }

   private void createFileSelection(String name, String argument, TextField textField, Button browseButton, String filter)
   {
      if (argument == null)
      {
         FileChooser fileChooser = new FileChooser();
         FileChooser.ExtensionFilter urdfFilter = new FileChooser.ExtensionFilter(name, filter);
         fileChooser.getExtensionFilters().add(urdfFilter);
         File browsePath = new File(prefs.get(textField.getId() + "_browse", System.getProperty("user.home")));
         fileChooser.setInitialDirectory(browsePath);

         browseButton.setOnAction((e) ->
         {

            File selectedFile = fileChooser.showOpenDialog(browseButton.getScene().getWindow());

            if (selectedFile != null)
            {
               textField.setText(selectedFile.getAbsolutePath());
               prefs.put(textField.getId(), selectedFile.getAbsolutePath());
            }
         });

         prefs.linkToPrefs(textField, "");

      }
      else
      {
         textField.setText(argument);
         textField.setDisable(true);
         browseButton.setVisible(false);
      }
   }

   /**
    * Set parameters that were used to start the application
    * 
    * @param loggerDistribution
    */
   public void setLoggerDistribution(String loggerDistribution)
   {
      Platform.runLater(() ->
      {
         createFileSelection("Logger distribution", loggerDistribution, logger_dist, browse_dist, "*.tar");
      });

   }
}
