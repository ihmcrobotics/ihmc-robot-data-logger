package us.ihmc.robotDataLogger.logger;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.function.DoubleConsumer;

import org.bytedeco.ffmpeg.avcodec.AVPacket;
import org.bytedeco.ffmpeg.avformat.AVFormatContext;
import org.bytedeco.ffmpeg.avformat.AVIOContext;
import org.bytedeco.ffmpeg.avformat.AVStream;
import org.bytedeco.ffmpeg.avutil.AVDictionary;
import org.bytedeco.ffmpeg.avutil.AVRational;
import org.bytedeco.javacpp.PointerPointer;

import static org.bytedeco.ffmpeg.global.avcodec.av_packet_alloc;
import static org.bytedeco.ffmpeg.global.avcodec.av_packet_free;
import static org.bytedeco.ffmpeg.global.avcodec.av_packet_rescale_ts;
import static org.bytedeco.ffmpeg.global.avcodec.av_packet_unref;
import static org.bytedeco.ffmpeg.global.avformat.AVFMT_NOFILE;
import static org.bytedeco.ffmpeg.global.avformat.AVIO_FLAG_WRITE;
import static org.bytedeco.ffmpeg.global.avformat.AVSEEK_FLAG_BACKWARD;
import static org.bytedeco.ffmpeg.global.avformat.av_interleaved_write_frame;
import static org.bytedeco.ffmpeg.global.avformat.av_read_frame;
import static org.bytedeco.ffmpeg.global.avformat.av_seek_frame;
import static org.bytedeco.ffmpeg.global.avformat.av_write_trailer;
import static org.bytedeco.ffmpeg.global.avformat.avformat_alloc_output_context2;
import static org.bytedeco.ffmpeg.global.avformat.avformat_close_input;
import static org.bytedeco.ffmpeg.global.avformat.avformat_find_stream_info;
import static org.bytedeco.ffmpeg.global.avformat.avformat_free_context;
import static org.bytedeco.ffmpeg.global.avformat.avformat_new_stream;
import static org.bytedeco.ffmpeg.global.avformat.avformat_open_input;
import static org.bytedeco.ffmpeg.global.avformat.avformat_write_header;
import static org.bytedeco.ffmpeg.global.avformat.avio_closep;
import static org.bytedeco.ffmpeg.global.avformat.avio_open;
import static org.bytedeco.ffmpeg.global.avcodec.avcodec_parameters_copy;
import static org.bytedeco.ffmpeg.global.avutil.AVMEDIA_TYPE_VIDEO;
import static org.bytedeco.ffmpeg.global.avutil.av_rescale_q;
import static org.bytedeco.ffmpeg.global.avutil.av_strerror;

/**
 * Losslessly crops a Magewell {@code .mov} recording by copying the encoded H.264 packets into a new container
 * (a "stream copy" / remux) instead of decoding and re-encoding. Because the packets are copied verbatim, the
 * cropped video is bit-for-bit identical to the source and can be re-cropped recursively with no generation loss.
 * <p>
 * H.264 packets can only be copied starting at a keyframe, so the requested start is snapped back to the nearest
 * keyframe at or before it; the copied frames before the requested start are the decode context for the first
 * in-window frame. Output timestamps are rebased so the first (keyframe) frame is at presentation time zero.
 * <p>
 * This class uses only the FFmpeg native libraries bundled with the {@code org.bytedeco:ffmpeg} dependency, the
 * same libraries {@link MagewellMuxer} and {@link MagewellDemuxer} already rely on, so it adds no new runtime
 * requirement.
 */
public class MagewellRemuxer
{
   /**
    * The presentation timestamp of one copied frame, in microseconds: {@code sourcePtsMicros} is its timestamp in
    * the source video, {@code outputPtsMicros} is its (rebased) timestamp in the cropped video. The caller pairs
    * these with robot timestamps to regenerate the timestamp sidecar file.
    */
   public record CroppedFrame(long sourcePtsMicros, long outputPtsMicros)
   {
   }

   private MagewellRemuxer()
   {
   }

   public static List<CroppedFrame> crop(File source, File destination, long startPtsMicros, long endPtsMicros) throws IOException
   {
      return crop(source, destination, startPtsMicros, endPtsMicros, null);
   }

   /**
    * Copies the video packets covering {@code [startPtsMicros, endPtsMicros]} (with the start snapped back to the
    * preceding keyframe) from {@code source} into {@code destination}, rebasing timestamps to start at zero.
    *
    * @param source           the source {@code .mov} recording.
    * @param destination      the cropped {@code .mov} to write; overwritten if it exists.
    * @param startPtsMicros   the requested start presentation timestamp, in microseconds.
    * @param endPtsMicros     the end presentation timestamp, in microseconds (inclusive).
    * @param progressConsumer optional, notified with a fraction in [0, 1] as packets are copied; may be {@code null}.
    * @return the copied frames in output order, so the caller can rebuild the timestamp file.
    */
   public static List<CroppedFrame> crop(File source,
                                         File destination,
                                         long startPtsMicros,
                                         long endPtsMicros,
                                         DoubleConsumer progressConsumer) throws IOException
   {
      AVFormatContext inputContext = new AVFormatContext(null);
      AVFormatContext outputContext = new AVFormatContext(null);
      AVPacket packet = null;
      boolean inputOpened = false;
      boolean outputFileOpened = false;

      try
      {
         // --- Open the source and locate the video stream ---
         checkError(avformat_open_input(inputContext, source.getAbsolutePath(), null, (AVDictionary) null), "opening " + source);
         inputOpened = true;
         checkError(avformat_find_stream_info(inputContext, (PointerPointer) null), "reading stream info");

         int videoStreamIndex = findVideoStreamIndex(inputContext);
         if (videoStreamIndex < 0)
            throw new IOException("No video stream found in " + source);
         AVStream inputStream = inputContext.streams(videoStreamIndex);
         AVRational inputTimeBase = inputStream.time_base();

         // --- Set up the destination container with a stream that copies the source codec parameters ---
         checkError(avformat_alloc_output_context2(outputContext, null, "mov", destination.getAbsolutePath()), "creating output context");
         AVStream outputStream = avformat_new_stream(outputContext, null);
         if (outputStream == null)
            throw new IOException("Could not allocate output stream");
         checkError(avcodec_parameters_copy(outputStream.codecpar(), inputStream.codecpar()), "copying codec parameters");
         outputStream.codecpar().codec_tag(0); // let the muxer choose the tag for this container

         // Carry over the source timing metadata so the cropped video reports the same time base and frame rate
         // as the original (e.g. 60 fps) rather than values the muxer would otherwise infer from the packets.
         outputStream.time_base(inputStream.time_base());
         outputStream.r_frame_rate(inputStream.r_frame_rate());
         outputStream.avg_frame_rate(inputStream.avg_frame_rate());

         if ((outputContext.oformat().flags() & AVFMT_NOFILE) == 0)
         {
            AVIOContext ioContext = new AVIOContext(null);
            checkError(avio_open(ioContext, destination.getAbsolutePath(), AVIO_FLAG_WRITE), "opening " + destination);
            outputContext.pb(ioContext);
            outputFileOpened = true;
         }
         checkError(avformat_write_header(outputContext, (AVDictionary) null), "writing header");
         AVRational outputTimeBase = outputStream.time_base(); // the muxer may pick its own time base

         AVRational microseconds = new AVRational().num(1).den(1_000_000);
         long startTimestamp = av_rescale_q(startPtsMicros, microseconds, inputTimeBase);
         long endTimestamp = av_rescale_q(endPtsMicros, microseconds, inputTimeBase);

         // --- Seek to the keyframe at or before the requested start, then copy packets up to the end ---
         checkError(av_seek_frame(inputContext, videoStreamIndex, startTimestamp, AVSEEK_FLAG_BACKWARD), "seeking to start keyframe");

         List<CroppedFrame> copiedFrames = new ArrayList<>();
         packet = av_packet_alloc();
         boolean haveRebaseOffset = false;
         long rebaseOffset = 0; // timestamp of the first copied frame, subtracted so output starts at zero

         while (av_read_frame(inputContext, packet) >= 0)
         {
            if (packet.stream_index() == videoStreamIndex)
            {
               long sourcePts = packet.pts();

               if (!haveRebaseOffset)
               {
                  rebaseOffset = packet.dts(); // first packet after a backward seek is the keyframe
                  haveRebaseOffset = true;
               }

               if (sourcePts > endTimestamp)
               {
                  av_packet_unref(packet);
                  break;
               }

               long sourcePtsMicros = av_rescale_q(sourcePts, inputTimeBase, microseconds);

               packet.pts(packet.pts() - rebaseOffset);
               packet.dts(packet.dts() - rebaseOffset);
               av_packet_rescale_ts(packet, inputTimeBase, outputTimeBase);
               long outputPtsMicros = av_rescale_q(packet.pts(), outputTimeBase, microseconds);

               packet.stream_index(outputStream.index());
               packet.pos(-1);
               checkError(av_interleaved_write_frame(outputContext, packet), "writing packet");

               copiedFrames.add(new CroppedFrame(sourcePtsMicros, outputPtsMicros));

               if (progressConsumer != null && endTimestamp > rebaseOffset)
                  progressConsumer.accept((double) (sourcePts - rebaseOffset) / (double) (endTimestamp - rebaseOffset));
            }

            av_packet_unref(packet);
         }

         checkError(av_write_trailer(outputContext), "finalizing " + destination);
         return copiedFrames;
      }
      finally
      {
         if (packet != null)
            av_packet_free(packet);
         if (outputFileOpened)
            avio_closep(outputContext.pb());
         avformat_free_context(outputContext);
         if (inputOpened)
            avformat_close_input(inputContext);
      }
   }

   private static int findVideoStreamIndex(AVFormatContext formatContext)
   {
      for (int i = 0; i < formatContext.nb_streams(); i++)
      {
         if (formatContext.streams(i).codecpar().codec_type() == AVMEDIA_TYPE_VIDEO)
            return i;
      }
      return -1;
   }

   private static void checkError(int returnCode, String whileDoing) throws IOException
   {
      if (returnCode < 0)
      {
         byte[] message = new byte[256];
         av_strerror(returnCode, message, message.length);
         throw new IOException("FFmpeg error while " + whileDoing + ": " + new String(message).trim() + " (" + returnCode + ")");
      }
   }
}
