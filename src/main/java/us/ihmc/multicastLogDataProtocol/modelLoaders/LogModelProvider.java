package us.ihmc.multicastLogDataProtocol.modelLoaders;

public interface LogModelProvider<T>
{
   public Class<? extends LogModelLoader<T>> getLoader();

   public String getModelName();

   public String[] getResourceDirectories();

   public byte[] getResourceZip();

   public byte[] getModel();

}
