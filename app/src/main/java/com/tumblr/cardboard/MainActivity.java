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
import android.util.Log;
import android.view.KeyEvent;

import com.android.volley.Response;
import com.android.volley.VolleyError;
import com.android.volley.toolbox.ImageRequest;
import com.google.vrtoolkit.cardboard.CardboardActivity;
import com.google.vrtoolkit.cardboard.CardboardView;
import com.google.vrtoolkit.cardboard.EyeTransform;
import com.google.vrtoolkit.cardboard.HeadTransform;
import com.google.vrtoolkit.cardboard.Viewport;
import com.tumblr.cardboard.networking.PhotoRequestQueue;
import com.tumblr.cardboard.networking.TumblrClient;
import com.tumblr.jumblr.types.PhotoPost;
import com.tumblr.jumblr.types.PhotoSize;
import com.tumblr.jumblr.types.Post;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;
import java.util.List;

import javax.microedition.khronos.egl.EGLConfig;

/**
 * A Cardboard sample application.
 */
public class MainActivity extends CardboardActivity implements CardboardView.StereoRenderer {

    private static final String TAG = MainActivity.class.getSimpleName();

    private static final float CAMERA_Z = 0.01f;
    private static final float TIME_DELTA = 0.3f;

    private static final float YAW_LIMIT = 0.12f;
    private static final float PITCH_LIMIT = 0.12f;

	private static final int NUM_TEXTURES = 8;
	private static final float SCALE_TV = 2f;
	private static final float SCALE_THEATER = 4f;

    // We keep the light always position just above the user.
    private final float[] mLightPosInWorldSpace = new float[] {0.0f, 2.0f, 0.0f, 1.0f};
    private final float[] mLightPosInEyeSpace = new float[4];

    private static final int COORDS_PER_VERTEX = 3;

    private final WorldLayoutData DATA = new WorldLayoutData();

    private FloatBuffer mFloorVertices;
    private FloatBuffer mFloorColors;
    private FloatBuffer mFloorNormals;

    private FloatBuffer mRectVertices;
    private FloatBuffer mRectColors;
    private FloatBuffer mRectFoundColors;
    private FloatBuffer mRectNormals;

	private FloatBuffer mRectTexCoords;

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

	private int[] mTextureHandle = new int[NUM_TEXTURES];;
	private int[] mRectTextureParam;
	private float[][] mImageRect;

    private float[][] mModelRect;

    private float[] mCamera;
    private float[] mView;
    private float[] mHeadView;
    private float[] mModelViewProjection;
    private float[] mModelView;

    private float[] mModelFloor;

    private int mSelected = -1;
    private float mObjectDistance = 8f;
    private float mFloorDepth = 20f;

	private int mNumImages = NUM_TEXTURES;

    private Vibrator mVibrator;

    private CardboardOverlayView mOverlayView;

	private TumblrClient mTumblrClient;
	private Bitmap[] mBitmaps;

	private class PostLoadTask extends AsyncTask<String, Void, List<Post>> {

		@Override
		protected List<Post> doInBackground(String... params) {
			return mTumblrClient.getPosts(params[0]);
		}

		@Override
		protected void onPostExecute(List<Post> posts) {
			Log.w(TAG, "Grabbed " + posts.size() + " images.");

			mNumImages = Math.min(NUM_TEXTURES, posts.size());

			for (int i = 0; i < mNumImages; i++) {
				final int finalI = i;

				List<PhotoSize> sizes = ((PhotoPost) posts.get(i)).getPhotos().get(0).getSizes();

				String url = sizes.get(0).getUrl();

				for (PhotoSize size : sizes) {
					if (size.getWidth() == 500) {
						url = size.getUrl();
					}
				}

				PhotoRequestQueue.getInstance(MainActivity.this).addToRequestQueue(new ImageRequest(
						url,
						new Response.Listener<Bitmap>() {
							@Override
							public void onResponse(Bitmap response) {
								if (response != null) {
									loadTexture(finalI, response);
									if (mSelected < 0) {
										mSelected = finalI;
										selectObject(finalI);
									} else {
										unselectObject(finalI);
									}
								} else {
									Log.e(TAG, "Null bitmap for " + finalI);
								}
							}
						}, 0, 0, null, new Response.ErrorListener() {
					@Override
					public void onErrorResponse(VolleyError error) {
						Log.e(TAG, "Could not load image.", error);
					}
				}
				));
			}
		}
	}

    /**
     * Converts a raw text file, saved as a resource, into an OpenGL ES shader
     * @param type The type of shader we will be creating.
     * @param resId The resource ID of the raw text file about to be turned into a shader.
     * @return
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
     * @param func
     */
    private static void checkGLError(String func) {
        int error;
        while ((error = GLES20.glGetError()) != GLES20.GL_NO_ERROR) {
            Log.e(TAG, func + ": glError " + error);
            throw new RuntimeException(func + ": glError " + error);
        }
    }

    /**
     * Sets the view to our CardboardView and initializes the transformation matrices we will use
     * to render our scene.
     * @param savedInstanceState
     */
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.common_ui);
        CardboardView cardboardView = (CardboardView) findViewById(R.id.cardboard_view);
        cardboardView.setRenderer(this);
        setCardboardView(cardboardView);

	    mImageRect = new float[NUM_TEXTURES][16];
        mModelRect = new float[NUM_TEXTURES][16];
        mCamera = new float[16];
        mView = new float[16];
        mModelViewProjection = new float[16];
        mModelView = new float[16];
        mModelFloor = new float[16];
        mHeadView = new float[16];
        mVibrator = (Vibrator) getSystemService(Context.VIBRATOR_SERVICE);

	    mRectTextureParam = new int[NUM_TEXTURES];
	    mBitmaps = new Bitmap[NUM_TEXTURES];

	    mTumblrClient = new TumblrClient();
	    new PostLoadTask().execute("landscape");

        mOverlayView = (CardboardOverlayView) findViewById(R.id.overlay);
        mOverlayView.show3DToast("Pull the magnet when you find an object.");
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
     * @param config The EGL configuration used when creating the surface.
     */
    @Override
    public void onSurfaceCreated(EGLConfig config) {
        Log.i(TAG, "onSurfaceCreated");
        GLES20.glClearColor(0.1f, 0.1f, 0.1f, 0.5f); // Dark background so text shows up well

        ByteBuffer bbVertices = ByteBuffer.allocateDirect(DATA.RECT_COORDS.length * 4);
        bbVertices.order(ByteOrder.nativeOrder());
        mRectVertices = bbVertices.asFloatBuffer();
        mRectVertices.put(DATA.RECT_COORDS);
        mRectVertices.position(0);

        ByteBuffer bbColors = ByteBuffer.allocateDirect(DATA.RECT_COLORS.length * 4);
        bbColors.order(ByteOrder.nativeOrder());
        mRectColors = bbColors.asFloatBuffer();
        mRectColors.put(DATA.RECT_COLORS);
        mRectColors.position(0);

        ByteBuffer bbFoundColors = ByteBuffer.allocateDirect(DATA.RECT_FOUND_COLORS.length * 4);
        bbFoundColors.order(ByteOrder.nativeOrder());
        mRectFoundColors = bbFoundColors.asFloatBuffer();
        mRectFoundColors.put(DATA.RECT_FOUND_COLORS);
        mRectFoundColors.position(0);

        ByteBuffer bbNormals = ByteBuffer.allocateDirect(DATA.RECT_NORMALS.length * 4);
        bbNormals.order(ByteOrder.nativeOrder());
        mRectNormals = bbNormals.asFloatBuffer();
        mRectNormals.put(DATA.RECT_NORMALS);
        mRectNormals.position(0);

	    ByteBuffer bbTextureCoordinates= ByteBuffer.allocateDirect(DATA.RECT_TEX_COORDS.length * 4);
	    bbTextureCoordinates.order(ByteOrder.nativeOrder());
	    mRectTexCoords = bbTextureCoordinates.asFloatBuffer();
	    mRectTexCoords.put(DATA.RECT_TEX_COORDS);
	    mRectTexCoords.position(0);

        // make a floor
        ByteBuffer bbFloorVertices = ByteBuffer.allocateDirect(DATA.FLOOR_COORDS.length * 4);
        bbFloorVertices.order(ByteOrder.nativeOrder());
        mFloorVertices = bbFloorVertices.asFloatBuffer();
        mFloorVertices.put(DATA.FLOOR_COORDS);
        mFloorVertices.position(0);

        ByteBuffer bbFloorNormals = ByteBuffer.allocateDirect(DATA.FLOOR_NORMALS.length * 4);
        bbFloorNormals.order(ByteOrder.nativeOrder());
        mFloorNormals = bbFloorNormals.asFloatBuffer();
        mFloorNormals.put(DATA.FLOOR_NORMALS);
        mFloorNormals.position(0);

        ByteBuffer bbFloorColors = ByteBuffer.allocateDirect(DATA.FLOOR_COLORS.length * 4);
        bbFloorColors.order(ByteOrder.nativeOrder());
        mFloorColors = bbFloorColors.asFloatBuffer();
        mFloorColors.put(DATA.FLOOR_COLORS);
        mFloorColors.position(0);

        int vertexShader = loadGLShader(GLES20.GL_VERTEX_SHADER, R.raw.light_vertex);
        int gridShader = loadGLShader(GLES20.GL_FRAGMENT_SHADER, R.raw.grid_fragment);

        mGlProgram = GLES20.glCreateProgram();
        GLES20.glAttachShader(mGlProgram, vertexShader);
        GLES20.glAttachShader(mGlProgram, gridShader);
        GLES20.glLinkProgram(mGlProgram);

        GLES20.glEnable(GLES20.GL_DEPTH_TEST);

	    prepareTextures();

	    Matrix.setIdentityM(mModelFloor, 0);
	    Matrix.translateM(mModelFloor, 0, 0, -mFloorDepth, 0); // Floor appears below user

        checkGLError("onSurfaceCreated");
    }

    /**
     * Converts a raw text file into a string.
     * @param resId The resource ID of the raw text file about to be turned into a shader.
     * @return
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

	public void prepareTextures() {
		for (int i = 0; i < NUM_TEXTURES; i++) {
			mRectTextureParam[i] = -1;
		}

		mTextureHandle = new int[NUM_TEXTURES];

//		GLES20.glGenTextures(mTextureHandle.length, mTextureHandle, 0);
	}

    /**
     * Prepares OpenGL ES before we draw a frame.
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
     * @param transform The transformations to apply to render this eye.
     */
    @Override
    public void onDrawEye(EyeTransform transform) {
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
        Matrix.multiplyMM(mView, 0, transform.getEyeView(), 0, mCamera, 0);

        // Set the position of the light
        Matrix.multiplyMV(mLightPosInEyeSpace, 0, mView, 0, mLightPosInWorldSpace, 0);
        GLES20.glUniform3f(mLightPosParam, mLightPosInEyeSpace[0], mLightPosInEyeSpace[1],
                mLightPosInEyeSpace[2]);

        // Build the ModelView and ModelViewProjection matrices
        // for calculating rect position and light.
	    for (int i = 0; i < mNumImages; i++) {
		    Matrix.multiplyMM(mModelView, 0, mView, 0, mModelRect[i], 0);
		    Matrix.multiplyMM(mModelViewProjection, 0, transform.getPerspective(), 0, mModelView, 0);
		    drawRect(i);
	    }

        // Set mModelView for the floor, so we draw floor in the correct location
        Matrix.multiplyMM(mModelView, 0, mView, 0, mModelFloor, 0);
        Matrix.multiplyMM(mModelViewProjection, 0, transform.getPerspective(), 0,
            mModelView, 0);
        drawFloor(transform.getPerspective());
    }

    @Override
    public void onFinishFrame(Viewport viewport) {
    }

    /**
     * Draw the rect. We've set all of our transformation matrices. Now we simply pass them into
     * the shader.
     */
    public void drawRect(int i) {
	    if (mRectTextureParam[i] < 0) {
		    // can't draw this rectangle
		    return;
	    }

        // This is not the floor!
        GLES20.glUniform1f(mIsFloorParam, 0f);

	    // Set the active texture unit
	    switch (i) {
		    case 0:
			    GLES20.glActiveTexture(GLES20.GL_TEXTURE0);
			    break;
		    case 1:
			    GLES20.glActiveTexture(GLES20.GL_TEXTURE1);
			    break;
		    case 2:
			    GLES20.glActiveTexture(GLES20.GL_TEXTURE2);
			    break;
		    case 3:
			    GLES20.glActiveTexture(GLES20.GL_TEXTURE3);
			    break;
		    case 4:
			    GLES20.glActiveTexture(GLES20.GL_TEXTURE4);
			    break;
		    case 5:
			    GLES20.glActiveTexture(GLES20.GL_TEXTURE5);
			    break;
		    case 6:
			    GLES20.glActiveTexture(GLES20.GL_TEXTURE6);
			    break;
		    case 7:
			    GLES20.glActiveTexture(GLES20.GL_TEXTURE7);
			    break;
	    }

	    checkGLError("active texture");

	    // Bind the texture to this unit.
	    GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, mRectTextureParam[i]);

	    GLES20.glTexParameterf(GLES20.GL_TEXTURE_2D,
			    GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE);
	    GLES20.glTexParameterf(GLES20.GL_TEXTURE_2D,
			    GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE);

	    // Set filtering
	    GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER,
			    GLES20.GL_NEAREST);
	    GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER,
			    GLES20.GL_LINEAR);

	    GLUtils.texImage2D(GLES20.GL_TEXTURE_2D, 0, mBitmaps[i], 0);

	    // Tell the texture uniform sampler to use this texture in the shader by binding to texture unit 0.
	    GLES20.glUniform1i(mRectTextureUniformParam, i);

        // Set the Model in the shader, used to calculate lighting
        GLES20.glUniformMatrix4fv(mModelParam, 1, false, mModelRect[i], 0);

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

        if (isLookingAtObject(i)) {
            GLES20.glVertexAttribPointer(mColorParam, 4, GLES20.GL_FLOAT, false,
                    0, mRectFoundColors);
        } else {
            GLES20.glVertexAttribPointer(mColorParam, 4, GLES20.GL_FLOAT, false,
                    0, mRectColors);
        }
        GLES20.glDrawArrays(GLES20.GL_TRIANGLES, 0, DATA.RECT_COORDS.length / 3); // 3 b/c triangles
        checkGLError("Drawing rect");
    }

    /**
     * Draw the floor. This feeds in data for the floor into the shader. Note that this doesn't
     * feed in data about position of the light, so if we rewrite our code to draw the floor first,
     * the lighting might look strange.
     */
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

	    int i = isLookingAtObject();

        if (i >= 0) {
	        if (i == mSelected) {
		        mOverlayView.show3DToast("Current: " + i);
	        } else {
		        mOverlayView.show3DToast("Selected: " + i);
		        if (mSelected >= 0 && mSelected < mNumImages) {
			        unselectObject(mSelected);
		        }
		        mSelected = i;
		        selectObject(mSelected);
	        }
        } else {
            mOverlayView.show3DToast("Look around to find the object!");
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
     * Find a new random position for the object.
     * We'll rotate it around the Y-axis so it's out of sight, and then up or down by a little bit.
     */
    private void selectObject(int i) {
	    Matrix.scaleM(mModelRect[i], 0, mImageRect[i], 0, SCALE_THEATER, SCALE_THEATER, 1f);
	    Matrix.translateM(mModelRect[i], 0, 0f, 0f, -mObjectDistance);
    }

	private void unselectObject(int i) {

		// First rotate in XZ plane.
		float azimuth = ((i + 1) * 320 / (mNumImages + 1) + 20) % 360;

		// Now get the up or down angle.
		float inclination = 0; // angle in Y plane
		if (i % 2 == 0) {
			inclination = 20;
		} else {
			inclination = -20;
		}

		float[] azimuthMatrix = new float[16];
		Matrix.setRotateM(azimuthMatrix, 0, azimuth, 0, 1, 0);

		float[] inclinationMatrix = new float[16];
		Matrix.setRotateM(inclinationMatrix, 0, inclination, 1, 0, 0);

		float[] rotationMatrix = new float[16];
		Matrix.multiplyMM(rotationMatrix, 0, azimuthMatrix, 0, inclinationMatrix, 0);

		Matrix.multiplyMM(mModelRect[i], 0, mImageRect[i], 0, rotationMatrix, 0);
		Matrix.translateM(mModelRect[i], 0, 0f, 0f, -mObjectDistance);
		Matrix.scaleM(mModelRect[i], 0, SCALE_TV, SCALE_TV, 1f);
	}

    /**
     * Check if user is looking at object by calculating where the object is in eye-space.
     * @return -1 if not looking at object
     */
    private int isLookingAtObject() {
        float[] initVec = {0, 0, 0, 1.0f};
        float[] objPositionVec = new float[4];

        for (int i = 0; i < mNumImages; i++) {
	        // Convert object space to camera space. Use the headView from onNewFrame.
	        Matrix.multiplyMM(mModelView, 0, mHeadView, 0, mModelRect[i], 0);
	        Matrix.multiplyMV(objPositionVec, 0, mModelView, 0, initVec, 0);

	        float pitch = (float)Math.atan2(objPositionVec[1], -objPositionVec[2]);
	        float yaw = (float)Math.atan2(objPositionVec[0], -objPositionVec[2]);

	        Log.i(TAG, "Object position: X: " + objPositionVec[0]
			        + "  Y: " + objPositionVec[1] + " Z: " + objPositionVec[2]);
	        Log.i(TAG, "Object Pitch: " + pitch +"  Yaw: " + yaw);

	        if ((Math.abs(pitch) < PITCH_LIMIT) && (Math.abs(yaw) < YAW_LIMIT)) {
		        return i;
	        }
        }

	    return -1;
    }


    /**
     * Check if user is looking at object by calculating where the object is in eye-space.
     */
    private boolean isLookingAtObject(int i) {
        float[] initVec = {0, 0, 0, 1.0f};
        float[] objPositionVec = new float[4];

	    // Convert object space to camera space. Use the headView from onNewFrame.
	    Matrix.multiplyMM(mModelView, 0, mHeadView, 0, mModelRect[i], 0);
	    Matrix.multiplyMV(objPositionVec, 0, mModelView, 0, initVec, 0);

	    float pitch = (float)Math.atan2(objPositionVec[1], -objPositionVec[2]);
	    float yaw = (float)Math.atan2(objPositionVec[0], -objPositionVec[2]);

	    Log.i(TAG, "Object position: X: " + objPositionVec[0]
			    + "  Y: " + objPositionVec[1] + " Z: " + objPositionVec[2]);
	    Log.i(TAG, "Object Pitch: " + pitch +"  Yaw: " + yaw);

	    return (Math.abs(pitch) < PITCH_LIMIT) && (Math.abs(yaw) < YAW_LIMIT);
    }

	public void loadTexture(int i, Bitmap bitmap) {

		Log.w(TAG, "loading texture: " + i);

		GLES20.glGenTextures(1, mTextureHandle, i);

		if (mTextureHandle[i] != 0) {
//			final BitmapFactory.Options options = new BitmapFactory.Options();
//			options.inScaled = false;   // No pre-scaling

			Log.w(TAG, "size: " + bitmap.getWidth() + ", " + bitmap.getHeight());

			// Set the active texture unit
			switch (i) {
				case 0:
					GLES20.glActiveTexture(GLES20.GL_TEXTURE0);
					break;
				case 1:
					GLES20.glActiveTexture(GLES20.GL_TEXTURE1);
					break;
				case 2:
					GLES20.glActiveTexture(GLES20.GL_TEXTURE2);
					break;
				case 3:
					GLES20.glActiveTexture(GLES20.GL_TEXTURE3);
					break;
				case 4:
					GLES20.glActiveTexture(GLES20.GL_TEXTURE4);
					break;
				case 5:
					GLES20.glActiveTexture(GLES20.GL_TEXTURE5);
					break;
				case 6:
					GLES20.glActiveTexture(GLES20.GL_TEXTURE6);
					break;
				case 7:
					GLES20.glActiveTexture(GLES20.GL_TEXTURE7);
					break;
			}

			Matrix.setIdentityM(mImageRect[i], 0);
			Matrix.scaleM(mImageRect[i], 0, 1f,
					(float) bitmap.getHeight() / bitmap.getWidth(), 1f);

			// Bind to the texture in OpenGL
			GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, mTextureHandle[i]);

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

			mBitmaps[i] = bitmap;

			// Recycle the bitmap, since its data has been loaded into OpenGL.
//			bitmap.recycle();

			mRectTextureParam[i] = mTextureHandle[i];
		}

		if (mTextureHandle[i] == 0) {
			Log.e(TAG, "Error loading texture.");
//				throw new RuntimeException("Error loading texture.");
		}
	}
}
