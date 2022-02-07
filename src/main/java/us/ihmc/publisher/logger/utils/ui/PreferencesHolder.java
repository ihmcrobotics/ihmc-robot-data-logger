package us.ihmc.publisher.logger.utils.ui;

import java.util.prefs.Preferences;

import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.TextInputControl;

public class PreferencesHolder
{
   private final Preferences prefs;

   public PreferencesHolder(Preferences prefs)
   {
      this.prefs = prefs;
   }

   public void linkToPrefs(TextInputControl field, String defaultValue)
   {
      field.setText(prefs.get(field.getId(), defaultValue));
      field.focusedProperty().addListener((arg0, oldVal, newVal) -> storeValue(newVal, field));
   }

   void storeValue(boolean focus, TextInputControl field)
   {
      if (!focus)
      {
         prefs.put(field.getId(), field.getText());
      }
   }

   public String get(String string, String property)
   {
      return prefs.get(string, property);
   }

   public void put(String id, String absolutePath)
   {
      prefs.put(id, absolutePath);

   }

   public void linkToPrefs(CheckBox field, boolean defaultValue)
   {
      field.setSelected(prefs.getBoolean(field.getId(), defaultValue));
      field.focusedProperty().addListener((arg0, oldVal, newVal) -> storeValue(newVal, field));
   }

   public void linkToPrefs(ComboBox<?> field, int defaultValue)
   {
      field.getSelectionModel().select(prefs.getInt(field.getId(), defaultValue));
      field.valueProperty().addListener((arg0, oldVal, newVal) ->
      {
         System.out.println("Setting field value to " + field.getSelectionModel().getSelectedIndex());
         prefs.putInt(field.getId(), field.getSelectionModel().getSelectedIndex());
      });

   }

   void storeValue(boolean focus, CheckBox field)
   {
      if (!focus)
      {
         prefs.putBoolean(field.getId(), field.isSelected());
      }
   }

   public boolean getBoolean(String string, boolean defaultValue)
   {
      return prefs.getBoolean(string, defaultValue);
   }

   public void putBoolean(String id, boolean value)
   {
      prefs.putBoolean(id, value);

   }

}
