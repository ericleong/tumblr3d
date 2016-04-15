package com.tumblr.cardboard.network;

import android.support.v4.util.Pair;
import com.tumblr.cardboard.BuildConfig;
import com.tumblr.jumblr.JumblrClient;
import com.tumblr.jumblr.types.PhotoPost;
import com.tumblr.jumblr.types.Post;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/**
 * Basic {@link com.tumblr.jumblr.JumblrClient} that downloads photos.
 * <p/>
 * Created by ericleong on 10/23/14.
 */
public class TumblrClient {

	private static final String TUMBLR_CONSUMER_KEY = BuildConfig.TUMBLR_CONSUMER_KEY;
	private static final String TUMBLR_SECRET_KEY = BuildConfig.TUMBLR_SECRET_KEY;

	private static final String TYPE_PHOTO = "photo";

	private JumblrClient mApi = new JumblrClient(TUMBLR_CONSUMER_KEY, TUMBLR_SECRET_KEY);

	/**
	 * @param query the tagged search parameter
	 * @return a list of photo posts
	 */
	public Pair<Long, List<PhotoPost>> getPosts(String query, long before) {
		final Map<String, String> options = new HashMap<String, String>();

		options.put("type", TYPE_PHOTO);
		if (before > 0) {
			options.put("before", Long.toString(before));
		}

		final List<Post> posts = mApi.tagged(query, options);

		final List<PhotoPost> photoPosts = new ArrayList<>(posts.size());

		long earliest = 0;

		final Iterator<Post> iter = posts.iterator();
		while (iter.hasNext()) {
			final Post post = iter.next();
			if (post instanceof PhotoPost) {
				photoPosts.add((PhotoPost) post);
			}

			earliest = post.getTimestamp();
		}

		return new Pair<>(earliest, photoPosts);
	}
}
