package com.tumblr.cardboard;

import android.content.Intent;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;

public class SearchActivity extends AppCompatActivity {

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_search);

		final EditText editText = (EditText) findViewById(R.id.search_term);
		final Button button = (Button) findViewById(R.id.start);

		editText.setText("cat gifs");

		button.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(final View v) {
				if (!TextUtils.isEmpty(editText.getText())) {
					Intent intent = new Intent(v.getContext(), Tumblr3DActivity.class);
					intent.putExtra(Tumblr3DActivity.EXTRA_SEARCH_TERM, editText.getText().toString());
					SearchActivity.this.startActivity(intent);
				}
			}
		});
	}
}
