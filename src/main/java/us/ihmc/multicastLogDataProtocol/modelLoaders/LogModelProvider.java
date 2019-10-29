package us.ihmc.multicastLogDataProtocol.modelLoaders;

public interface LogModelProvider<T>
{
   public Class<? extends T> getLoader();

   public String getModelName();

   public String[] getResourceDirectories();

   public byte[] getResourceZip();

   public byte[] getModel();

}
