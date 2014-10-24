package com.tumblr.cardboard.networking;

import com.tumblr.cardboard.BuildConfig;
import com.tumblr.jumblr.JumblrClient;
import com.tumblr.jumblr.types.Post;

import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/**
 * Created by ericleong on 10/23/14.
 */
public class TumblrClient {

	private static final String TUMBLR_CLIENT_ID = BuildConfig.TUMBLR_CLIENT_ID;
	private static final String TUMBLR_SECRET = BuildConfig.TUMBLR_SECRET;

	private static final String TYPE_PHOTO = "photo";

	private JumblrClient mApi = new JumblrClient(TUMBLR_CLIENT_ID, TUMBLR_SECRET);

	public List<Post> getPosts(String query) {
		final Map<String, String> options = new HashMap<String, String>();

		options.put("type", TYPE_PHOTO);

		final List<Post> posts = mApi.tagged(query, options);
		final Iterator<Post> iter = posts.iterator();
		while(iter.hasNext()) {
			final Post post = iter.next();
			if (!post.getType().equals(TYPE_PHOTO)) {
				iter.remove();
			}
		}

		return posts;
	}
}
