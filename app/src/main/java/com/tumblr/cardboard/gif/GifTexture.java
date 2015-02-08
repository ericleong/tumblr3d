package com.tumblr.cardboard.gif;

import android.annotation.TargetApi;
import android.content.Context;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.os.Build;
import android.view.Gravity;

import com.bumptech.glide.gifdecoder.GifDecoder;
import com.bumptech.glide.gifdecoder.GifHeader;
import com.bumptech.glide.load.Transformation;
import com.bumptech.glide.load.engine.bitmap_recycle.BitmapPool;

/**
 * Plays the frames of an animated GIF.
 */
public class GifTexture implements GifFrameLoader.FrameCallback {

	/** A constant indicating that an animated gifTexture should loop continuously. */
	public static final int LOOP_FOREVER = -1;
	/**
	 * A constant indicating that an animated gifTexture should loop for its default number of times. For animated GIFs,
	 * this constant indicates the GIF should use the netscape loop count if present.
	 */
	public static final int LOOP_INTRINSIC = 0;

	private final GifState state;
	private final GifDecoder decoder;
	private final GifFrameLoader frameLoader;

	/** True if the gifTexture is currently animating. */
	private boolean isRunning;
	/** True if the gifTexture should animate while visible. */
	private boolean isStarted;
	/** True if the gifTexture's resources have been recycled. */
	private boolean isRecycled;
	/** The number of times we've looped over all the frames in the gif. */
	private int loopCount;
	/** The number of times to loop through the gif animation. */
	private int maxLoopCount = LOOP_FOREVER;

	/** Listener that is called when the gif needs to be updatd. */
	private GifUpdateListener gifUpdateListener;

	/**
	 * Implement for frame updates.
	 */
	public interface GifUpdateListener {

		/**
		 * Similar to {@link com.tumblr.cardboard.gif.GifFrameLoader.FrameCallback}, notifies the
		 * listener that a new frame is prepared and should be displayed.
		 *
		 * @param bitmap the current frame to display
		 */
		public void onFrameUpdate(Bitmap bitmap);
	}

	/**
	 * Constructor for GifDrawable.
	 *
	 * @see #setFrameTransformation(com.bumptech.glide.load.Transformation, android.graphics.Bitmap)
	 *
	 * @param context A context.
	 * @param bitmapProvider An {@link com.bumptech.glide.gifdecoder.GifDecoder.BitmapProvider} that can be used to
	 *                       retrieve re-usable {@link android.graphics.Bitmap}s.
	 * @param bitmapPool A {@link com.bumptech.glide.load.engine.bitmap_recycle.BitmapPool} that can be used to return
	 *                   the first frame when this gifTexture is recycled.
	 * @param frameTransformation An {@link com.bumptech.glide.load.Transformation} that can be applied to each frame.
	 * @param targetFrameWidth The desired width of the frames displayed by this gifTexture (the width of the view or
	 *                         {@link com.bumptech.glide.request.target.Target} this gifTexture is being loaded into).
	 * @param targetFrameHeight The desired height of the frames displayed by this gifTexture (the height of the view or
	 *                          {@link com.bumptech.glide.request.target.Target} this gifTexture is being loaded into).
	 * @param gifHeader The header data for this gif.
	 * @param data The full bytes of the gif.
	 * @param firstFrame The decoded and transformed first frame of this gif.
	 */
	public GifTexture(Context context, GifDecoder.BitmapProvider bitmapProvider, BitmapPool bitmapPool,
	                   Transformation<Bitmap> frameTransformation, int targetFrameWidth, int targetFrameHeight,
	                   GifHeader gifHeader, byte[] data, Bitmap firstFrame) {
		this(new GifState(gifHeader, data, context, frameTransformation, targetFrameWidth, targetFrameHeight,
				bitmapProvider, bitmapPool, firstFrame));
	}

	GifTexture(GifState state) {
		if (state == null) {
			throw new NullPointerException("GifState must not be null");
		}

		this.state = state;
		this.decoder = new GifDecoder(state.bitmapProvider);
		decoder.setData(state.gifHeader, state.data);
		frameLoader = new GifFrameLoader(state.context, this, decoder, state.targetWidth, state.targetHeight);
	}

	// Visible for testing.
	GifTexture(GifDecoder decoder, GifFrameLoader frameLoader, Bitmap firstFrame, BitmapPool bitmapPool) {
		this.decoder = decoder;
		this.frameLoader = frameLoader;
		this.state = new GifState(null);
		state.bitmapPool = bitmapPool;
		state.firstFrame = firstFrame;
	}

	public Bitmap getFirstFrame() {
		return state.firstFrame;
	}

	public void setFrameTransformation(Transformation<Bitmap> frameTransformation, Bitmap firstFrame) {
		if (firstFrame == null) {
			throw new NullPointerException("The first frame of the GIF must not be null");
		}
		if (frameTransformation == null) {
			throw new NullPointerException("The frame transformation must not be null");
		}
		state.frameTransformation = frameTransformation;
		state.firstFrame = firstFrame;
		frameLoader.setFrameTransformation(frameTransformation);
	}

	public GifDecoder getDecoder() {
		return decoder;
	}

	public Transformation<Bitmap> getFrameTransformation() {
		return state.frameTransformation;
	}

	public byte[] getData() {
		return state.data;
	}

	public int getFrameCount() {
		return decoder.getFrameCount();
	}

	private void resetLoopCount() {
		loopCount = 0;
	}

	public void start() {
		isStarted = true;
		resetLoopCount();
		startRunning();
	}

	public void stop() {
		isStarted = false;
		stopRunning();

		// On APIs > honeycomb we know our gifTexture is not being displayed anymore when it's callback is cleared and so
		// we can use the absence of a callback as an indication that it's ok to clear our temporary data. Prior to
		// honeycomb we can't tell if our callback is null and instead eagerly reset to avoid holding on to resources we
		// no longer need.
		if (Build.VERSION.SDK_INT < Build.VERSION_CODES.HONEYCOMB) {
			reset();
		}
	}

	/**
	 * Clears temporary data and resets the gifTexture back to the first frame.
	 */
	private void reset() {
		frameLoader.clear();
		updateListener();
	}

	private void startRunning() {
		// If we have only a single frame, we don't want to decode it endlessly.
		if (decoder.getFrameCount() == 1) {
			updateListener();
		}  else if (!isRunning) {
			isRunning = true;
			frameLoader.start();
			updateListener();
		}
	}

	private void stopRunning() {
		isRunning = false;
		frameLoader.stop();
	}

	public int getIntrinsicWidth() {
		return state.firstFrame.getWidth();
	}

	public int getIntrinsicHeight() {
		return state.firstFrame.getHeight();
	}

	public boolean isRunning() {
		return isRunning;
	}

	// For testing.
	void setIsRunning(boolean isRunning) {
		this.isRunning = isRunning;
	}

	@TargetApi(Build.VERSION_CODES.HONEYCOMB)
	@Override
	public void onFrameReady(int frameIndex) {
		updateListener();

		if (frameIndex == decoder.getFrameCount() - 1) {
			loopCount++;
		}

		if (maxLoopCount != LOOP_FOREVER && loopCount >= maxLoopCount) {
			stop();
		}
	}

	/**
	 * Clears any resources for loading frames that are currently held on to by this object.
	 */
	public void recycle() {
		isRecycled = true;
		state.bitmapPool.put(state.firstFrame);
		frameLoader.clear();
		frameLoader.stop();
	}

	// For testing.
	boolean isRecycled() {
		return isRecycled;
	}

	public void setLoopCount(int loopCount) {
		if (loopCount <= 0 && loopCount != LOOP_FOREVER && loopCount != LOOP_INTRINSIC) {
			throw new IllegalArgumentException("Loop count must be greater than 0, or equal to "
					+ "GlideDrawable.LOOP_FOREVER, or equal to GlideDrawable.LOOP_INTRINSIC");
		}

		if (loopCount == LOOP_INTRINSIC) {
			maxLoopCount = decoder.getLoopCount();
		} else {
			maxLoopCount = loopCount;
		}
	}

	public void setGifUpdateListener(GifUpdateListener gifUpdateListener) {
		this.gifUpdateListener = gifUpdateListener;
	}

	private void updateListener() {
		if (gifUpdateListener != null && frameLoader.getCurrentFrame() != null) {
			gifUpdateListener.onFrameUpdate(frameLoader.getCurrentFrame());
		}
	}

	static class GifState {
		private static final int GRAVITY = Gravity.FILL;
		GifHeader gifHeader;
		byte[] data;
		Context context;
		Transformation<Bitmap> frameTransformation;
		int targetWidth;
		int targetHeight;
		GifDecoder.BitmapProvider bitmapProvider;
		BitmapPool bitmapPool;
		Bitmap firstFrame;

		public GifState(GifHeader header, byte[] data, Context context,
		                Transformation<Bitmap> frameTransformation, int targetWidth, int targetHeight,
		                GifDecoder.BitmapProvider provider, BitmapPool bitmapPool, Bitmap firstFrame) {
			if (firstFrame == null) {
				throw new NullPointerException("The first frame of the GIF must not be null");
			}
			gifHeader = header;
			this.data = data;
			this.bitmapPool = bitmapPool;
			this.firstFrame = firstFrame;
			this.context = context.getApplicationContext();
			this.frameTransformation = frameTransformation;
			this.targetWidth = targetWidth;
			this.targetHeight = targetHeight;
			bitmapProvider = provider;
		}

		public GifState(GifState original) {
			if (original != null) {
				gifHeader = original.gifHeader;
				data = original.data;
				context = original.context;
				frameTransformation = original.frameTransformation;
				targetWidth = original.targetWidth;
				targetHeight = original.targetHeight;
				bitmapProvider = original.bitmapProvider;
				bitmapPool = original.bitmapPool;
				firstFrame = original.firstFrame;
			}
		}

		public GifTexture newDrawable(Resources res) {
			return newDrawable();
		}

		public GifTexture newDrawable() {
			return new GifTexture(this);
		}

		public int getChangingConfigurations() {
			return 0;
		}
	}
}
