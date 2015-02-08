package com.tumblr.cardboard.gif;

import com.bumptech.glide.load.engine.Resource;
import com.bumptech.glide.util.Util;

/**
 * A resource wrapping a {@link com.tumblr.cardboard.gif.GifTexture}.
 */
public class GifTextureResource implements Resource<GifTexture> {

	protected final GifTexture gifTexture;

	public GifTextureResource(GifTexture gifTexture) {
		this.gifTexture = gifTexture;
	}

	@Override
	public GifTexture get() {
		return gifTexture;
	}

	@Override
	public int getSize() {
		return gifTexture.getData().length + Util.getBitmapByteSize(gifTexture.getFirstFrame());
	}

	@Override
	public void recycle() {
		gifTexture.stop();
		gifTexture.recycle();
	}
}
