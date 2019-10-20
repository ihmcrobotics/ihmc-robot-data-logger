package us.ihmc.multicastLogDataProtocol.modelLoaders;

public interface LogModelLoader<T>
{
   public void load(String modelName, byte[] model, String[] resourceDirectories, byte[] resourceZip);

   public T createRobot();

   public String getModelName();

   public byte[] getModel();

   public String[] getResourceDirectories();

   public byte[] getResourceZip();
}
