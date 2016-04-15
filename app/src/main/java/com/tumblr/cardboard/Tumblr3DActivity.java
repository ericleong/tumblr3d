/*
 * Copyright 2014 Google Inc. All Rights Reserved.

 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.tumblr.cardboard;

import android.content.Context;
import android.graphics.Bitmap;
import android.opengl.GLES20;
import android.opengl.GLUtils;
import android.opengl.Matrix;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Vibrator;
import android.support.v4.util.Pair;
import android.util.Log;
import android.view.KeyEvent;
import com.bumptech.glide.Glide;
import com.bumptech.glide.request.target.Target;
import com.google.vrtoolkit.cardboard.CardboardActivity;
import com.google.vrtoolkit.cardboard.CardboardView;
import com.google.vrtoolkit.cardboard.Eye;
import com.google.vrtoolkit.cardboard.HeadTransform;
import com.google.vrtoolkit.cardboard.Viewport;
import com.tumblr.cardboard.gif.GifResourceDecoder;
import com.tumblr.cardboard.network.TumblrClient;
import com.tumblr.jumblr.types.PhotoPost;
import com.tumblr.jumblr.types.PhotoSize;

import javax.microedition.khronos.egl.EGLConfig;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;
import java.util.LinkedList;
import java.util.List;
import java.util.Queue;

/**
 * Displays Tumblr photo posts in 3D!
 * <p/>
 * Created for Tumblr's Fall 2014 hackathon.
 * Updated for Tumblr's Spring 2016 hackathon.
 */
@SuppressWarnings("SpellCheckingInspection")
public class Tumblr3DActivity extends CardboardActivity implements CardboardView.StereoRenderer, Texturizer {

	private static final String TAG = Tumblr3DActivity.class.getSimpleName();

	private static final int INVALID_TEXTURE = 0;
	private static final int REFRESH_TEXTURE_ID = 0;

	private static final float CAMERA_Z = 0.01f;

	private static final float Z_NEAR = 0.1f;
	private static final float Z_FAR = 100.0f;

	private static final float YAW_LIMIT = 0.12f;
	private static final float PITCH_LIMIT = 0.12f;

	/**
	 * 16 photos + 1 refresh icon.
	 */
	private static final int NUM_IMAGES_STATIC = 1;
	private static final int NUM_IMAGES_DYNAMIC = 16;
	private static final int NUM_TEXTURES = NUM_IMAGES_DYNAMIC + NUM_IMAGES_STATIC;
	private static final int DESIRED_PHOTO_SIZE = 500;
	private static final float SCALE_TV = 3f;
	private static final float SCALE_TV_VR = 8f;
	private static final float SCALE_THEATER = 6f;
	private static final float SCALE_THEATER_VR = 20f;

	private final float SPHERE_RADIUS = 40f;
	@SuppressWarnings("FieldCanBeLocal")
	private final float FLOOR_DEPTH = 20f;

	// We keep the light always position just above the user.
	private final float[] mLightPosInWorldSpace = new float[]{0.0f, 2.0f, 0.0f, 1.0f};
	private final float[] mLightPosInEyeSpace = new float[4];

	private static final int COORDS_PER_VERTEX = 3;

	private FloatBuffer mFloorVertices;
	private FloatBuffer mFloorColors;
	private FloatBuffer mFloorNormals;

	private FloatBuffer mRectVertices;
	private FloatBuffer mRectColors;
	private FloatBuffer mRectFoundColors;
	private FloatBuffer mRectNormals;

	private FloatBuffer mRectTexCoords;

	private float mScaleTV;
	private float mScaleTheater;

	private int mGlProgram;
	private int mPositionParam;
	private int mNormalParam;
	private int mColorParam;
	private int mModelViewProjectionParam;
	private int mLightPosParam;
	private int mModelViewParam;
	private int mModelParam;
	private int mIsFloorParam;

	private int mRectTextureUniformParam;
	private int mRectTextureCoordinateParam;

	private int[] mTextureIds = new int[NUM_TEXTURES];
	private int[] mRectTextureIds;
	private float[][] mImageRect;
	private Target<?>[] mTargets = new Target<?>[NUM_TEXTURES];

	private float[][] mModelRect;

	private float[] mCamera;
	private float[] mView;
	private float[] mHeadView;
	private float[] mModelViewProjection;
	private float[] mModelView;

	private float[] mModelFloor;

	private int mSelectedTexIndex = -1;

	private int mNumImages = NUM_IMAGES_DYNAMIC;

	private Vibrator mVibrator;

	private CardboardOverlayView mOverlayView;

	private TumblrClient mTumblrClient;
	private long mBefore;
	private AsyncTask<?, ?, ?> mLoadTask;

	/**
	 * Notify OpenGL to create these textures.
	 */
	private final Queue<PhotoTexture> mWaitingPhotoTextures = new LinkedList<>();
	/**
	 * Notify OpenGL to update these textures.
	 */
	private final Queue<PhotoTexture> mUpdatingPhotoTextures = new LinkedList<>();

	private List<PhotoPost> mCurrentPosts;

	private GifResourceDecoder mGifResourceDecoder;

	public void updateOrCreateTexture(int texIndex, Bitmap bitmap, boolean recycle, boolean force) {
		if (mTextureIds[texIndex] == INVALID_TEXTURE || force) {
			Log.d(TAG, "Request to create " + texIndex);
			synchronized (mWaitingPhotoTextures) {
				mWaitingPhotoTextures.add(new PhotoTexture(texIndex, bitmap, recycle));
			}
		} else {
			Log.d(TAG, "Request to load " + texIndex);
			synchronized (mUpdatingPhotoTextures) {
				mUpdatingPhotoTextures.add(new PhotoTexture(texIndex, bitmap, false));
			}
		}
	}

	/**
	 * Loads photo posts in the background.
	 */
	private class PostLoadTask extends AsyncTask<String, Void, Pair<Long, List<PhotoPost>>> {

		@Override
		protected Pair<Long, List<PhotoPost>> doInBackground(String... params) {
			Log.w(TAG, "Loading posts for " + params[0] + " before: " + mBefore);
			return mTumblrClient.getPosts(params[0], mBefore);
		}

		@Override
		protected void onPostExecute(Pair<Long, List<PhotoPost>> result) {

			if (Tumblr3DActivity.this.isDestroyed()) {
				return;
			}

			mBefore = result.first;

			mNumImages = Math.min(NUM_IMAGES_DYNAMIC, result.second.size());

			for (int i = 0; i < mNumImages; i++) {
				List<PhotoSize> sizes = result.second.get(i).getPhotos().get(0).getSizes();

				String url = sizes.get(0).getUrl();

				for (PhotoSize size : sizes) {
					if (size.getWidth() == DESIRED_PHOTO_SIZE) {
						url = size.getUrl();
					}
				}

				final int texIndex = NUM_IMAGES_STATIC + i;

				if (mTargets[texIndex] != null) {
					Glide.clear(mTargets[texIndex]);
					mTargets[texIndex].onDestroy();
				}

				final Target<?> target;

				if (url.endsWith(".gif")) {
					PhotoTexture.GifTextureTarget gifTarget =
							new PhotoTexture.GifTextureTarget(Tumblr3DActivity.this, mGifResourceDecoder, texIndex);

					target = gifTarget;

					Glide.with(Tumblr3DActivity.this).load(url).asGif().toBytes().into(gifTarget);
				} else {
					PhotoTexture.TextureTarget photoTarget =
							new PhotoTexture.TextureTarget(texIndex, Tumblr3DActivity.this);

					target = photoTarget;

					Glide.with(Tumblr3DActivity.this).load(url).asBitmap().into(photoTarget);
				}

				mTargets[texIndex] = target;
			}

			mCurrentPosts = result.second;
		}
	}

	/**
	 * Converts a raw text file, saved as a resource, into an OpenGL ES shader
	 *
	 * @param type  The type of shader we will be creating.
	 * @param resId The resource ID of the raw text file about to be turned into a shader.
	 * @return the id of the shader
	 */
	private int loadGLShader(int type, int resId) {
		String code = readRawTextFile(resId);
		int shader = GLES20.glCreateShader(type);
		GLES20.glShaderSource(shader, code);
		GLES20.glCompileShader(shader);

		// Get the compilation status.
		final int[] compileStatus = new int[1];
		GLES20.glGetShaderiv(shader, GLES20.GL_COMPILE_STATUS, compileStatus, 0);

		// If the compilation failed, delete the shader.
		if (compileStatus[0] == 0) {
			Log.e(TAG, "Error compiling shader: " + GLES20.glGetShaderInfoLog(shader));
			GLES20.glDeleteShader(shader);
			shader = 0;
		}

		if (shader == 0) {
			throw new RuntimeException("Error creating shader.");
		}

		return shader;
	}

	/**
	 * Checks if we've had an error inside of OpenGL ES, and if so what that error is.
	 *
	 * @param func the name of the function that was just called (for debugging)
	 */
	private static void checkGLError(String func) {
		int error;
		//noinspection LoopStatementThatDoesntLoop
		while ((error = GLES20.glGetError()) != GLES20.GL_NO_ERROR) {
			Log.e(TAG, func + ": glError " + error);
			throw new RuntimeException(func + ": glError " + error);
		}
	}

	/**
	 * Sets the view to our CardboardView and initializes the transformation matrices we will use
	 * to render our scene.
	 *
	 * @param savedInstanceState If the activity is being re-initialized after
	 *     previously being shut down then this Bundle contains the data it most
	 *     recently supplied in {@link #onSaveInstanceState}.  <b><i>Note: Otherwise it is null.</i></b>
	 */
	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);

		setContentView(R.layout.common_ui);
		CardboardView cardboardView = (CardboardView) findViewById(R.id.cardboard_view);
		cardboardView.setRenderer(this);
		setCardboardView(cardboardView);
		cardboardView.setVRModeEnabled(true);

		mScaleTV = cardboardView.getVRMode() ? SCALE_TV_VR : SCALE_TV;
		mScaleTheater = cardboardView.getVRMode() ? SCALE_THEATER_VR : SCALE_THEATER;

		mImageRect = new float[NUM_TEXTURES][16];
		mModelRect = new float[NUM_TEXTURES][16];
		mCamera = new float[16];
		mView = new float[16];
		mModelViewProjection = new float[16];
		mModelView = new float[16];
		mModelFloor = new float[16];
		mHeadView = new float[16];
		mVibrator = (Vibrator) getSystemService(Context.VIBRATOR_SERVICE);

		mRectTextureIds = new int[NUM_TEXTURES];

		for (int i = 0; i < NUM_TEXTURES; i++) {
			mRectTextureIds[i] = -1;
		}

		mTextureIds = new int[NUM_TEXTURES];

		mGifResourceDecoder = new GifResourceDecoder(this);
		mTumblrClient = new TumblrClient();

		mOverlayView = (CardboardOverlayView) findViewById(R.id.overlay);

		Glide.with(this).fromResource().asBitmap().load(R.drawable.ic_refresh_white_24dp)
				.into(new PhotoTexture.TextureTarget(REFRESH_TEXTURE_ID, this));
	}

	@Override
	protected void onResume() {
		super.onResume();

		if (mLoadTask == null || mLoadTask.getStatus() == AsyncTask.Status.FINISHED) {
			mLoadTask = new PostLoadTask().execute(getSearchTerm());
		}
	}

	@Override
	public void onRendererShutdown() {
		Log.i(TAG, "onRendererShutdown");
	}

	@Override
	public void onSurfaceChanged(int width, int height) {
		Log.i(TAG, "onSurfaceChanged");
	}

	/**
	 * Creates the buffers we use to store information about the 3D world. OpenGL doesn't use Java
	 * arrays, but rather needs data in a format it can understand. Hence we use ByteBuffers.
	 *
	 * @param config The EGL configuration used when creating the surface.
	 */
	@Override
	public void onSurfaceCreated(EGLConfig config) {
		Log.i(TAG, "onSurfaceCreated");
		GLES20.glClearColor(0.1f, 0.1f, 0.1f, 0.5f); // Dark background so text shows up well

		ByteBuffer bbVertices = ByteBuffer.allocateDirect(WorldLayoutData.RECT_COORDS.length * 4);
		bbVertices.order(ByteOrder.nativeOrder());
		mRectVertices = bbVertices.asFloatBuffer();
		mRectVertices.put(WorldLayoutData.RECT_COORDS);
		mRectVertices.position(0);

		ByteBuffer bbColors = ByteBuffer.allocateDirect(WorldLayoutData.RECT_COLORS.length * 4);
		bbColors.order(ByteOrder.nativeOrder());
		mRectColors = bbColors.asFloatBuffer();
		mRectColors.put(WorldLayoutData.RECT_COLORS);
		mRectColors.position(0);

		ByteBuffer bbFoundColors = ByteBuffer.allocateDirect(WorldLayoutData.RECT_FOUND_COLORS.length * 4);
		bbFoundColors.order(ByteOrder.nativeOrder());
		mRectFoundColors = bbFoundColors.asFloatBuffer();
		mRectFoundColors.put(WorldLayoutData.RECT_FOUND_COLORS);
		mRectFoundColors.position(0);

		ByteBuffer bbNormals = ByteBuffer.allocateDirect(WorldLayoutData.RECT_NORMALS.length * 4);
		bbNormals.order(ByteOrder.nativeOrder());
		mRectNormals = bbNormals.asFloatBuffer();
		mRectNormals.put(WorldLayoutData.RECT_NORMALS);
		mRectNormals.position(0);

		ByteBuffer bbTextureCoordinates = ByteBuffer.allocateDirect(WorldLayoutData.RECT_TEX_COORDS.length * 4);
		bbTextureCoordinates.order(ByteOrder.nativeOrder());
		mRectTexCoords = bbTextureCoordinates.asFloatBuffer();
		mRectTexCoords.put(WorldLayoutData.RECT_TEX_COORDS);
		mRectTexCoords.position(0);

		// make a floor
		ByteBuffer bbFloorVertices = ByteBuffer.allocateDirect(WorldLayoutData.FLOOR_COORDS.length * 4);
		bbFloorVertices.order(ByteOrder.nativeOrder());
		mFloorVertices = bbFloorVertices.asFloatBuffer();
		mFloorVertices.put(WorldLayoutData.FLOOR_COORDS);
		mFloorVertices.position(0);

		ByteBuffer bbFloorNormals = ByteBuffer.allocateDirect(WorldLayoutData.FLOOR_NORMALS.length * 4);
		bbFloorNormals.order(ByteOrder.nativeOrder());
		mFloorNormals = bbFloorNormals.asFloatBuffer();
		mFloorNormals.put(WorldLayoutData.FLOOR_NORMALS);
		mFloorNormals.position(0);

		ByteBuffer bbFloorColors = ByteBuffer.allocateDirect(WorldLayoutData.FLOOR_COLORS.length * 4);
		bbFloorColors.order(ByteOrder.nativeOrder());
		mFloorColors = bbFloorColors.asFloatBuffer();
		mFloorColors.put(WorldLayoutData.FLOOR_COLORS);
		mFloorColors.position(0);

		int vertexShader = loadGLShader(GLES20.GL_VERTEX_SHADER, R.raw.light_vertex);
		int gridShader = loadGLShader(GLES20.GL_FRAGMENT_SHADER, R.raw.flat_fragment);

		mGlProgram = GLES20.glCreateProgram();
		GLES20.glAttachShader(mGlProgram, vertexShader);
		GLES20.glAttachShader(mGlProgram, gridShader);
		GLES20.glLinkProgram(mGlProgram);

		GLES20.glEnable(GLES20.GL_DEPTH_TEST);

		Matrix.setIdentityM(mModelFloor, 0);
		Matrix.translateM(mModelFloor, 0, 0, -FLOOR_DEPTH, 0); // Floor appears below user

		checkGLError("onSurfaceCreated");
	}

	/**
	 * Converts a raw text file into a string.
	 *
	 * @param resId The resource ID of the raw text file about to be turned into a shader.
	 * @return the contents of the text file
	 */
	private String readRawTextFile(int resId) {
		InputStream inputStream = getResources().openRawResource(resId);
		try {
			BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream));
			StringBuilder sb = new StringBuilder();
			String line;
			while ((line = reader.readLine()) != null) {
				sb.append(line).append("\n");
			}
			reader.close();
			return sb.toString();
		} catch (IOException e) {
			e.printStackTrace();
		}
		return "";
	}

	/**
	 * Prepares OpenGL ES before we draw a frame.
	 *
	 * @param headTransform The head transformation in the new frame.
	 */
	@Override
	public void onNewFrame(HeadTransform headTransform) {
		GLES20.glUseProgram(mGlProgram);

		mModelViewProjectionParam = GLES20.glGetUniformLocation(mGlProgram, "u_MVP");
		mLightPosParam = GLES20.glGetUniformLocation(mGlProgram, "u_LightPos");
		mModelViewParam = GLES20.glGetUniformLocation(mGlProgram, "u_MVMatrix");
		mModelParam = GLES20.glGetUniformLocation(mGlProgram, "u_Model");
		mIsFloorParam = GLES20.glGetUniformLocation(mGlProgram, "u_IsFloor");
		mRectTextureUniformParam = GLES20.glGetUniformLocation(mGlProgram, "u_Texture");

		// load gif updates into OpenGL
		synchronized (mUpdatingPhotoTextures) {
			while (!mUpdatingPhotoTextures.isEmpty()) {
				PhotoTexture texture = mUpdatingPhotoTextures.remove();
				updateTexture(texture.texIndex, texture.bitmap);
			}
		}

		// load new photos into OpenGL
		synchronized (mWaitingPhotoTextures) {
			// load downloaded photos into OpenGL
			while (!mWaitingPhotoTextures.isEmpty()) {
				PhotoTexture texture = mWaitingPhotoTextures.remove();
				loadTextureInternal(texture.texIndex, texture.bitmap, texture.recycle);

				if (texture.texIndex >= NUM_IMAGES_STATIC) {
					// First image that loads shows up in the "theater!"
					if (mSelectedTexIndex < 0) {
						mSelectedTexIndex = texture.texIndex;
						selectPhoto(texture.texIndex - NUM_IMAGES_STATIC);
					} else {
						// Put image in the right spot
						unselectPhoto(texture.texIndex - NUM_IMAGES_STATIC);
					}
				} else if (texture.texIndex == REFRESH_TEXTURE_ID) {
					placePhoto(mModelRect, mImageRect, texture.texIndex, 1, -180, 30, SPHERE_RADIUS / 2);
				}
			}
		}

		// Build the Model part of the ModelView matrix.
//        Matrix.rotateM(mModelRect, 0, TIME_DELTA, 0.5f, 0.5f, 1.0f);

		// Build the camera matrix and apply it to the ModelView.
		Matrix.setLookAtM(mCamera, 0, 0.0f, 0.0f, CAMERA_Z, 0.0f, 0.0f, 0.0f, 0.0f, 1.0f, 0.0f);

		headTransform.getHeadView(mHeadView, 0);

		checkGLError("onReadyToDraw");
	}

	/**
	 * Draws a frame for an eye. The transformation for that eye (from the camera) is passed in as
	 * a parameter.
	 *
	 * @param eye The transformations to apply to render this eye.
	 */
	@Override
	public void onDrawEye(Eye eye) {
		GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT | GLES20.GL_DEPTH_BUFFER_BIT);

		mPositionParam = GLES20.glGetAttribLocation(mGlProgram, "a_Position");
		mNormalParam = GLES20.glGetAttribLocation(mGlProgram, "a_Normal");
		mColorParam = GLES20.glGetAttribLocation(mGlProgram, "a_Color");
		mRectTextureCoordinateParam = GLES20.glGetAttribLocation(mGlProgram, "a_TexCoordinate");

		GLES20.glEnableVertexAttribArray(mPositionParam);
		GLES20.glEnableVertexAttribArray(mNormalParam);
		GLES20.glEnableVertexAttribArray(mColorParam);
		checkGLError("mColorParam");

		// Apply the eye transformation to the camera.
		Matrix.multiplyMM(mView, 0, eye.getEyeView(), 0, mCamera, 0);

		// Set the position of the light
		Matrix.multiplyMV(mLightPosInEyeSpace, 0, mView, 0, mLightPosInWorldSpace, 0);
		GLES20.glUniform3f(mLightPosParam, mLightPosInEyeSpace[0], mLightPosInEyeSpace[1],
				mLightPosInEyeSpace[2]);

		// Set mModelView for the floor, so we draw floor in the correct location
		Matrix.multiplyMM(mModelView, 0, mView, 0, mModelFloor, 0);
		Matrix.multiplyMM(mModelViewProjection, 0, eye.getPerspective(Z_NEAR, Z_FAR), 0,
				mModelView, 0);
		drawFloor(eye.getPerspective(Z_NEAR, Z_FAR));

		// Build the ModelView and ModelViewProjection matrices
		// for calculating rect position and light.
		for (int i = 0; i < mModelRect.length; i++) {
			Matrix.multiplyMM(mModelView, 0, mView, 0, mModelRect[i], 0);
			Matrix.multiplyMM(mModelViewProjection, 0, eye.getPerspective(Z_NEAR, Z_FAR), 0, mModelView, 0);
			drawRect(i);
		}
	}

	@Override
	public void onFinishFrame(Viewport viewport) {
	}

	/**
	 * Draw the rect. We've set all of our transformation matrices. Now we simply pass them into
	 * the shader.
	 */
	public void drawRect(int texIndex) {
		if (mRectTextureIds[texIndex] < INVALID_TEXTURE) {
			// can't draw this rectangle
			return;
		}

		// This is not the floor!
		GLES20.glUniform1f(mIsFloorParam, 0f);

		// Set the active texture unit
		GLES20.glActiveTexture(GLES20.GL_TEXTURE0 + texIndex);

		// Bind the texture to this unit.
		GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, mRectTextureIds[texIndex]);

		// Tell the texture uniform sampler to use this texture in the shader by binding to texture unit 0.
		GLES20.glUniform1i(mRectTextureUniformParam, texIndex);

		// Set the Model in the shader, used to calculate lighting
		GLES20.glUniformMatrix4fv(mModelParam, 1, false, mModelRect[texIndex], 0);

		// Set the ModelView in the shader, used to calculate lighting
		GLES20.glUniformMatrix4fv(mModelViewParam, 1, false, mModelView, 0);

		// Set the position of the rect
		GLES20.glVertexAttribPointer(mPositionParam, COORDS_PER_VERTEX, GLES20.GL_FLOAT,
				false, 0, mRectVertices);

		// Set the ModelViewProjection matrix in the shader.
		GLES20.glUniformMatrix4fv(mModelViewProjectionParam, 1, false, mModelViewProjection, 0);

		// Set the normal positions of the rect, again for shading
		GLES20.glVertexAttribPointer(mNormalParam, 3, GLES20.GL_FLOAT,
				false, 0, mRectNormals);

		// Connect texBuffer to "aTextureCoord".
		GLES20.glVertexAttribPointer(mRectTextureCoordinateParam, 2,
				GLES20.GL_FLOAT, false, 0, mRectTexCoords);

		// Enable the "aTextureCoord" vertex attribute.
		GLES20.glEnableVertexAttribArray(mRectTextureCoordinateParam);

		if (texIndex == mSelectedTexIndex || isLookingAtObject(texIndex)) {
			GLES20.glVertexAttribPointer(mColorParam, 4, GLES20.GL_FLOAT, false,
					0, mRectFoundColors);
		} else {
			GLES20.glVertexAttribPointer(mColorParam, 4, GLES20.GL_FLOAT, false,
					0, mRectColors);
		}
		GLES20.glDrawArrays(GLES20.GL_TRIANGLES, 0, WorldLayoutData.RECT_COORDS.length / 3); // 3 b/c triangles
		checkGLError("Drawing rect");
	}

	/**
	 * Draw the floor. This feeds in data for the floor into the shader. Note that this doesn't
	 * feed in data about position of the light, so if we rewrite our code to draw the floor first,
	 * the lighting might look strange.
	 */
	@SuppressWarnings("UnusedParameters")
	public void drawFloor(float[] perspective) {
		// This is the floor!
		GLES20.glUniform1f(mIsFloorParam, 1f);

		// Set ModelView, MVP, position, normals, and color
		GLES20.glUniformMatrix4fv(mModelParam, 1, false, mModelFloor, 0);
		GLES20.glUniformMatrix4fv(mModelViewParam, 1, false, mModelView, 0);
		GLES20.glUniformMatrix4fv(mModelViewProjectionParam, 1, false, mModelViewProjection, 0);
		GLES20.glVertexAttribPointer(mPositionParam, COORDS_PER_VERTEX, GLES20.GL_FLOAT,
				false, 0, mFloorVertices);
		GLES20.glVertexAttribPointer(mNormalParam, 3, GLES20.GL_FLOAT, false, 0, mFloorNormals);
		GLES20.glVertexAttribPointer(mColorParam, 4, GLES20.GL_FLOAT, false, 0, mFloorColors);
		GLES20.glDrawArrays(GLES20.GL_TRIANGLES, 0, 6);

		checkGLError("drawing floor");
	}

	/**
	 * Increment the score, hide the object, and give feedback if the user pulls the magnet while
	 * looking at the object. Otherwise, remind the user what to do.
	 */
	@Override
	public void onCardboardTrigger() {
		Log.i(TAG, "onCardboardTrigger");

		int texIndex = isLookingAtObject();

		if (texIndex >= 0) {
			if (texIndex >= NUM_IMAGES_STATIC) {
				final int photoIndex = texIndex - NUM_IMAGES_STATIC;
				if (mCurrentPosts != null && photoIndex < mCurrentPosts.size()) {
					mOverlayView.show3DToast(mCurrentPosts.get(photoIndex).getBlogName());
				}
				if (texIndex != mSelectedTexIndex) {
					if (photoIndex >= 0 && photoIndex < mNumImages) {
						unselectPhoto(mSelectedTexIndex - NUM_IMAGES_STATIC);
					}
					mSelectedTexIndex = texIndex;
					selectPhoto(photoIndex);
				}
			} else if (texIndex == REFRESH_TEXTURE_ID) {
				mOverlayView.show3DToast("Refreshing");
				mLoadTask = new PostLoadTask().execute(getSearchTerm());
			}
		} else {
			mOverlayView.show3DToast("Select objects when they are highlighted!");
		}
		// Always give user feedback
		mVibrator.vibrate(50);
	}

	@Override
	public boolean onKeyDown(int keyCode, KeyEvent event) {
		if (keyCode == KeyEvent.KEYCODE_VOLUME_UP) {
			onCardboardTrigger();
			return true;
		}
		return super.onKeyDown(keyCode, event);
	}

	/**
	 * Moves the texture to the middle and makes it big.
	 *
	 * @param photoIndex the index of the texture to move
	 */
	private void selectPhoto(int photoIndex) {
		final int i = NUM_IMAGES_STATIC + photoIndex;
		Matrix.scaleM(mModelRect[i], 0, mImageRect[i], 0, mScaleTheater, mScaleTheater, 1f);
		Matrix.translateM(mModelRect[i], 0, 0f, 0f, -SPHERE_RADIUS);
	}

	/**
	 * Move the texture back to its original location.
	 *
	 * @param photoIndex the index of the texture to move
	 */
	private void unselectPhoto(int photoIndex) {

		// First rotate in XZ plane.
		float azimuth = ((photoIndex + 1) * 300 / (mNumImages + 1) + 30) % 360;

		// Now get the up or down angle.
		float inclination; // angle in Y plane
		if (photoIndex % 2 == 0) {
			inclination = 20;
		} else {
			inclination = -20;
		}

		final int i = NUM_IMAGES_STATIC + photoIndex;

		placePhoto(mModelRect, mImageRect, i, mScaleTV, azimuth, inclination, -SPHERE_RADIUS);
	}

	private static void placePhoto(float[][] modelRects, float[][] imageRects, int texIndex,
	                                float scale, float azimuth, float inclination, float yTranslate) {
		float[] azimuthMatrix = new float[16];
		Matrix.setRotateM(azimuthMatrix, 0, azimuth, 0, 1, 0);

		float[] inclinationMatrix = new float[16];
		Matrix.setRotateM(inclinationMatrix, 0, inclination, 1, 0, 0);

		float[] rotationMatrix = new float[16];
		Matrix.multiplyMM(rotationMatrix, 0, azimuthMatrix, 0, inclinationMatrix, 0);

		Matrix.multiplyMM(modelRects[texIndex], 0, imageRects[texIndex], 0, rotationMatrix, 0);
		Matrix.translateM(modelRects[texIndex], 0, 0f, 0f, yTranslate);
		Matrix.scaleM(modelRects[texIndex], 0, scale, scale, 1f);

	}

	/**
	 * Check if user is looking at object by calculating where the object is in eye-space.
	 *
	 * @return -1 if not looking at object
	 */
	private int isLookingAtObject() {
		float[] initVec = {0, 0, 0, 1.0f};
		float[] objPositionVec = new float[4];

		for (int i = 0; i < mModelRect.length; i++) {
			if (isLookingAtObject(initVec, objPositionVec, i)) {
				return i;
			}
		}

		return -1;
	}


	/**
	 * Check if user is looking at object by calculating where the object is in eye-space.
	 */
	private boolean isLookingAtObject(int texIndex) {
		float[] initVec = {0, 0, 0, 1.0f};
		float[] objPositionVec = new float[4];

		return isLookingAtObject(initVec, objPositionVec, texIndex);
	}

	private boolean isLookingAtObject(float[] initVec, float[] objPositionVec, int texIndex) {
		// Convert object space to camera space. Use the headView from onNewFrame.
		Matrix.multiplyMM(mModelView, 0, mHeadView, 0, mModelRect[texIndex], 0);
		Matrix.multiplyMV(objPositionVec, 0, mModelView, 0, initVec, 0);

		float pitch = (float) Math.atan2(objPositionVec[1], -objPositionVec[2]);
		float yaw = (float) Math.atan2(objPositionVec[0], -objPositionVec[2]);

		Log.v(TAG, "Object position: X: " + objPositionVec[0]
				+ "  Y: " + objPositionVec[1] + " Z: " + objPositionVec[2]);
		Log.v(TAG, "Object Pitch: " + pitch + "  Yaw: " + yaw);

		return (Math.abs(pitch) < PITCH_LIMIT) && (Math.abs(yaw) < YAW_LIMIT);
	}

	/**
	 * Loads a bitmap into OpenGL.
	 *
	 * @param texIndex the desired texture index
	 * @param bitmap   the bitmap to put into OpenGL
	 */
	private void loadTextureInternal(int texIndex, Bitmap bitmap, boolean recycle) {

		GLES20.glGenTextures(1, mTextureIds, texIndex);

		Log.d(TAG, "loading texture: " + texIndex + " -> " + mTextureIds[texIndex]);

		if (mTextureIds[texIndex] != INVALID_TEXTURE && bitmap != null && !bitmap.isRecycled()) {

			// Set the active texture unit
			GLES20.glActiveTexture(GLES20.GL_TEXTURE0 + texIndex);

			Matrix.setIdentityM(mImageRect[texIndex], 0);
			Matrix.scaleM(mImageRect[texIndex], 0, 1f,
					(float) bitmap.getHeight() / bitmap.getWidth(), 1f);

			// Bind to the texture in OpenGL
			GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, mTextureIds[texIndex]);

			GLES20.glTexParameterf(GLES20.GL_TEXTURE_2D,
					GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE);
			GLES20.glTexParameterf(GLES20.GL_TEXTURE_2D,
					GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE);

			// Set filtering
			GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER,
					GLES20.GL_NEAREST);
			GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER,
					GLES20.GL_LINEAR);

			// Load the bitmap into the bound texture.
			GLUtils.texImage2D(GLES20.GL_TEXTURE_2D, 0, bitmap, 0);

			mRectTextureIds[texIndex] = mTextureIds[texIndex];
		} else {
			Log.w(TAG, "Failed to load: " + texIndex);
		}

		if (mTextureIds[texIndex] == INVALID_TEXTURE) {
			Log.e(TAG, "Error loading texture.");
		}
	}

	private void updateTexture(int texIndex, Bitmap bitmap) {
		if (mTextureIds[texIndex] != INVALID_TEXTURE && bitmap != null && !bitmap.isRecycled()) {

			// Set the active texture unit
			GLES20.glActiveTexture(GLES20.GL_TEXTURE0 + texIndex);

			Matrix.setIdentityM(mImageRect[texIndex], 0);
			Matrix.scaleM(mImageRect[texIndex], 0, 1f,
					(float) bitmap.getHeight() / bitmap.getWidth(), 1f);

			// Bind to the texture in OpenGL
			GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, mTextureIds[texIndex]);

			// Load the bitmap into the bound texture.
			GLUtils.texImage2D(GLES20.GL_TEXTURE_2D, 0, bitmap, 0);
		} else {
			Log.w(TAG, "Failed to update: " + texIndex + " val: " + mTextureIds[texIndex]);
		}
	}

	public String getSearchTerm() {
		return "rave gifs";
	}
}
