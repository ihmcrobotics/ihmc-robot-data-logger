package us.ihmc.multicastLogDataProtocol.modelLoaders;

public interface LogModelProvider
{
   public Class<?> getLoader();

   public String getModelName();

   public String[] getTopLevelResourceDirectories();

   public byte[] getResourceZip();

   public byte[] getModel();

}
