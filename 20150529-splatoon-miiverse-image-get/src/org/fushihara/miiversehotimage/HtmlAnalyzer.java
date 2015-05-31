package org.fushihara.miiversehotimage;

import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class HtmlAnalyzer {
	private static final Pattern mP1 = Pattern.compile("^(.+?)\"");
	private static final Pattern mP2 = Pattern
			.compile("<p class=\"post-content-memo\"><img src=\"(.+?)\"");

	public static ZonedDateTime getLastHotDateFromHtml(String html) {
		Pattern pat = Pattern
				.compile("class=\"button selected\">(\\d+)/(\\d+)/(\\d+)</a>");
		Matcher match = pat.matcher(html);
		if (match.find()) {
			int year = Integer.parseInt(match.group(1));
			int month = Integer.parseInt(match.group(2));
			int date = Integer.parseInt(match.group(3));
			return ZonedDateTime.of(year, month, date, 0, 0, 0, 0,
					ZoneId.of("Asia/Tokyo"));
		} else {
			throw new IllegalArgumentException("htmlから日付をマッチ出来ませんでした\n"
					+ html.replaceAll("\t|\n|\r", ""));
		}
	}

	public static List<IllustAttackedPostData> getPostDataListFromRawHtml(
			String rawHtml) {
		List<IllustAttackedPostData> result = new ArrayList<HtmlAnalyzer.IllustAttackedPostData>();
		// 投稿ごとに分割
		String[] htmls1 = rawHtml.split("<div id=\"post-");
		for (String htmls2 : htmls1) {
			// idを取得
			String postId;
			{
				Matcher m = mP1.matcher(htmls2);
				if (!m.find()) {
					continue;
				}
				postId = m.group(1);
			}
			// class="post-memo" の有無を確認
			if (htmls2.contains("class=\"post-memo\"")) {
				// イラストurlを取得
				String illustUrl;
				{
					Matcher m = mP2.matcher(htmls2);
					if (!m.find()) {
						continue;
					}
					illustUrl = m.group(1);
				}
				result.add(new IllustAttackedPostData(postId, illustUrl));
			} else {
				result.add(new IllustAttackedPostData(postId));
			}
		}
		return result;
	}

	public static class IllustAttackedPostData {
		/** like:AYMHAAACAAADVHkH-62vaQ */
		private final String mPostId;
		/** like:https://d3esbfg30x759i.cloudfront.net/pap/zlCfzTZfim8yeQjXfo */
		private final String mIllustUrl;
		private final boolean hasIllust;

		public IllustAttackedPostData(String postId, String illustUrl) {
			mPostId = postId;
			mIllustUrl = illustUrl;
			hasIllust = true;
		}

		public IllustAttackedPostData(String postId) {
			mPostId = postId;
			mIllustUrl = null;
			hasIllust = false;
		}

		public boolean hasIllust() {
			return hasIllust;
		}

		public String getIllustUrl() {
			return mIllustUrl;
		}
		public String getPostId(){
			return mPostId;
		}

		@Override
		public int hashCode() {
			final int prime = 31;
			int result = 1;
			result = prime * result
					+ ((mPostId == null) ? 0 : mPostId.hashCode());
			return result;
		}

		@Override
		public boolean equals(Object obj) {
			if (this == obj)
				return true;
			if (obj == null)
				return false;
			if (getClass() != obj.getClass())
				return false;
			IllustAttackedPostData other = (IllustAttackedPostData) obj;
			if (mPostId == null) {
				if (other.mPostId != null)
					return false;
			} else if (!mPostId.equals(other.mPostId))
				return false;
			return true;
		}

		@Override
		public String toString() {
			if (hasIllust) {
				return mPostId + " " + mIllustUrl;
			} else {
				return mPostId;
			}
		}
	}
}
