/*
 * Copyright (C) 2015-2021 Samuel Audet
 *
 * Licensed either under the Apache License, Version 2.0, or (at your option)
 * under the terms of the GNU General Public License as published by
 * the Free Software Foundation (subject to the "Classpath" exception),
 * either version 2, or any later version (collectively, the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *     http://www.gnu.org/licenses/
 *     http://www.gnu.org/software/classpath/license.html
 *
 * or as provided in the LICENSE.txt file that accompanied this code.
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.bytedeco.javacv;

import java.nio.Buffer;
import java.nio.ByteBuffer;
import org.bytedeco.javacpp.BytePointer;
import org.bytedeco.javacpp.Loader;
import org.bytedeco.javacpp.Pointer;

import org.bytedeco.opencv.opencv_core.*;
import static org.bytedeco.opencv.global.opencv_core.*;

/**
 * A utility class to map data between {@link Frame} and {@link IplImage} or {@link Mat}.
 * Since this is an abstract class, one must choose between two concrete classes:
 * {@link ToIplImage} or {@link ToMat}. {@link ToOrgOpenCvCoreMat} is also available to
 * do the same with {@link org.opencv.core.Mat} from the official Java API of OpenCV.
 *
 * @author Samuel Audet
 */
public abstract class OpenCVFrameConverter<F> extends FrameConverter<F> {
   static { Loader.load(org.bytedeco.opencv.global.opencv_core.class); }

   IplImage img;
   Mat mat;
   org.opencv.core.Mat orgOpenCvCoreMat;

   public static class ToIplImage extends OpenCVFrameConverter<IplImage> {
      @Override public Frame convert(IplImage img) { return super.convert(img); }
      @Override public IplImage convert(Frame frame) { return convertToIplImage(frame); }
   }

   public static class ToMat extends OpenCVFrameConverter<Mat> {
      @Override public Frame convert(Mat mat) { return super.convert(mat); }
      @Override public Mat convert(Frame frame) { return convertToMat(frame); }
   }

   public static class ToOrgOpenCvCoreMat extends OpenCVFrameConverter<org.opencv.core.Mat> {
      @Override public Frame convert(org.opencv.core.Mat mat) { return super.convert(mat); }
      @Override public org.opencv.core.Mat convert(Frame frame) { return convertToOrgOpenCvCoreMat(frame); }
   }

   public static int getFrameDepth(int depth) {
      switch (depth) {
         case IPL_DEPTH_8U:  case CV_8U:  return Frame.DEPTH_UBYTE;
         case IPL_DEPTH_8S:  case CV_8S:  return Frame.DEPTH_BYTE;
         case IPL_DEPTH_16U: case CV_16U: return Frame.DEPTH_USHORT;
         case IPL_DEPTH_16S: case CV_16S: return Frame.DEPTH_SHORT;
         case IPL_DEPTH_32F: case CV_32F: return Frame.DEPTH_FLOAT;
         case IPL_DEPTH_32S: case CV_32S: return Frame.DEPTH_INT;
         case IPL_DEPTH_64F: case CV_64F: return Frame.DEPTH_DOUBLE;
         default: return -1;
      }
   }

   public static int getIplImageDepth(int depth) {
      switch (depth) {
         case Frame.DEPTH_UBYTE:  return IPL_DEPTH_8U;
         case Frame.DEPTH_BYTE:   return IPL_DEPTH_8S;
         case Frame.DEPTH_USHORT: return IPL_DEPTH_16U;
         case Frame.DEPTH_SHORT:  return IPL_DEPTH_16S;
         case Frame.DEPTH_FLOAT:  return IPL_DEPTH_32F;
         case Frame.DEPTH_INT:    return IPL_DEPTH_32S;
         case Frame.DEPTH_DOUBLE: return IPL_DEPTH_64F;
         default:  return -1;
      }
   }
   static boolean isEqual(Frame frame, IplImage img) {
      return img != null && frame != null && frame.image != null && frame.image.length > 0
             && frame.imageWidth == img.width() && frame.imageHeight == img.height()
             && frame.imageChannels == img.nChannels() && getIplImageDepth(frame.imageDepth) == img.depth()
             && new Pointer(frame.image[0].position(0)).address() == img.imageData().address()
             && frame.imageStride * Math.abs(frame.imageDepth) / 8 == img.widthStep();
   }
   public IplImage convertToIplImage(Frame frame) {
      if (frame == null || frame.image == null) {
         return null;
      } else if (frame.opaque instanceof IplImage) {
         return (IplImage)frame.opaque;
      } else if (!isEqual(frame, img)) {
         int depth = getIplImageDepth(frame.imageDepth);
         if (img != null) {
            img.releaseReference();
         }
         img = depth < 0 ? null : (IplImage)IplImage.create(frame.imageWidth, frame.imageHeight, depth, frame.imageChannels, new Pointer(frame.image[0].position(0)))
                                                    .widthStep(frame.imageStride * Math.abs(frame.imageDepth) / 8)
                                                    .imageSize(frame.image[0].capacity() * Math.abs(frame.imageDepth) / 8).retainReference();
      }
      return img;
   }
   public Frame convert(IplImage img) {
      if (img == null) {
         return null;
      } else if (!isEqual(frame, img)) {
         frame = new Frame();
         frame.imageWidth = img.width();
         frame.imageHeight = img.height();
         frame.imageDepth = getFrameDepth(img.depth());
         frame.imageChannels = img.nChannels();
         frame.imageStride = img.widthStep() * 8 / Math.abs(frame.imageDepth);
         frame.image = new Buffer[] { img.createBuffer() };
      }
      frame.opaque = img;
      return frame;
   }

   public static int getMatDepth(int depth) {
      switch (depth) {
         case Frame.DEPTH_UBYTE:  return CV_8U;
         case Frame.DEPTH_BYTE:   return CV_8S;
         case Frame.DEPTH_USHORT: return CV_16U;
         case Frame.DEPTH_SHORT:  return CV_16S;
         case Frame.DEPTH_FLOAT:  return CV_32F;
         case Frame.DEPTH_INT:    return CV_32S;
         case Frame.DEPTH_DOUBLE: return CV_64F;
         default:  return -1;
      }
   }
   static boolean isEqual(Frame frame, Mat mat) {
      return mat != null && frame != null && frame.image != null && frame.image.length > 0
             && frame.imageWidth == mat.cols() && frame.imageHeight == mat.rows()
             && frame.imageChannels == mat.channels() && getMatDepth(frame.imageDepth) == mat.depth()
             && new Pointer(frame.image[0].position(0)).address() == mat.data().address()
             && frame.imageStride * Math.abs(frame.imageDepth) / 8 == (int)mat.step();
   }
   public Mat convertToMat(Frame frame) {
      if (frame == null || frame.image == null) {
         return null;
      } else if (frame.opaque instanceof Mat) {
         return (Mat)frame.opaque;
      } else if (!isEqual(frame, mat)) {
         int depth = getMatDepth(frame.imageDepth);
         if (mat != null) {
            mat.releaseReference();
         }
         mat = depth < 0 ? null : (Mat)new Mat(frame.imageHeight, frame.imageWidth, CV_MAKETYPE(depth, frame.imageChannels),
                                               new Pointer(frame.image[0].position(0)), frame.imageStride * Math.abs(frame.imageDepth) / 8).retainReference();
      }
      return mat;
   }
   public Frame convert(Mat mat) {
      if (mat == null) {
         return null;
      } else if (!isEqual(frame, mat)) {
         frame = new Frame();
         frame.imageWidth = mat.cols();
         frame.imageHeight = mat.rows();
         frame.imageDepth = getFrameDepth(mat.depth());
         frame.imageChannels = mat.channels();
         frame.imageStride = (int)mat.step() * 8 / Math.abs(frame.imageDepth);
         frame.image = new Buffer[] { mat.createBuffer() };
      }
      frame.opaque = mat;
      return frame;
   }

   static boolean isEqual(Frame frame, org.opencv.core.Mat mat) {
      return mat != null && frame != null && frame.image != null && frame.image.length > 0
             && frame.imageWidth == mat.cols() && frame.imageHeight == mat.rows()
             && frame.imageChannels == mat.channels() && getMatDepth(frame.imageDepth) == mat.depth()
             && new Pointer(frame.image[0].position(0)).address() == mat.dataAddr();
   }
   public org.opencv.core.Mat convertToOrgOpenCvCoreMat(Frame frame) {
      if (frame == null || frame.image == null) {
         return null;
      } else if (frame.opaque instanceof org.opencv.core.Mat) {
         return (org.opencv.core.Mat)frame.opaque;
      } else if (!isEqual(frame, mat)) {
         int depth = getMatDepth(frame.imageDepth);
         orgOpenCvCoreMat = depth < 0 ? null : new org.opencv.core.Mat(frame.imageHeight, frame.imageWidth,
                                                                       CV_MAKETYPE(depth, frame.imageChannels), new BytePointer(new Pointer(frame.image[0].position(0)))
                                                                             .capacity(frame.image[0].capacity() * Math.abs(frame.imageDepth) / 8).asByteBuffer(),
                                                                       frame.imageStride * Math.abs(frame.imageDepth) / 8);
      }
      return orgOpenCvCoreMat;
   }
   public Frame convert(final org.opencv.core.Mat mat) {
      if (mat == null) {
         return null;
      } else if (!isEqual(frame, mat)) {
         frame = new Frame();
         frame.imageWidth = mat.cols();
         frame.imageHeight = mat.rows();
         frame.imageDepth = getFrameDepth(mat.depth());
         frame.imageChannels = mat.channels();
         frame.imageStride = (int)mat.step1();
         ByteBuffer byteBuffer = new BytePointer() { { address = mat.dataAddr(); } }.capacity(mat.rows() * mat.step1() * mat.elemSize1()).asByteBuffer();
         switch (mat.depth()) {
            case CV_8U:
            case CV_8S:
               frame.image = new Buffer[] { byteBuffer };
               break;
            case CV_16U:
            case CV_16S:
               frame.image = new Buffer[] { byteBuffer.asShortBuffer() };
               break;
            case CV_32F:
               frame.image = new Buffer[] { byteBuffer.asFloatBuffer() };
               break;
            case CV_32S:
               frame.image = new Buffer[] { byteBuffer.asIntBuffer() };
               break;
            case CV_64F:
               frame.image = new Buffer[] { byteBuffer.asDoubleBuffer() };
               break;
            default:
               frame.image = null;
               break;
         }
      }
      frame.opaque = mat;
      return frame;
   }

   @Override public void close() {
      super.close();
      if (img != null) {
         img.releaseReference();
         img = null;
      }
      if (mat != null) {
         mat.releaseReference();
         mat = null;
      }
      if (orgOpenCvCoreMat != null) {
         orgOpenCvCoreMat.release();
         orgOpenCvCoreMat = null;
      }
   }
}