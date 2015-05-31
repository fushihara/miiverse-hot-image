package org.fushihara.miiversehotimage;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;

import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpUriRequest;
import org.apache.http.client.methods.RequestBuilder;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.util.EntityUtils;
import org.fushihara.miiversehotimage.HtmlAnalyzer.IllustAttackedPostData;

public class HttpManager {
	public static class HttpManagerBuilder {
		private File mSaveBaseDirectory = null;
		private String mTitle = "";
		/** ミーバースのurlに含まれているゲーム個別のID ddddd/ddddd 形式 */
		private String mBaseUrl = "";
		private String mTargetDate = "";
		/** ミーバースから取得した最新のhotの日付を後からセットする時はtrue、明示的に日付を指定する時はfalse */
		private boolean isAutoTargetDateSet = true;
		private CloseableHttpClient mHttpClient = null;

		public HttpManagerBuilder setSaveBaseDirectory(String directoryPath)
				throws IOException {
			File file = new File(directoryPath);
			if (file.exists() && file.isFile()) {
				throw new IOException("指定のパスはファイルが既に存在しています " + directoryPath);
			}
			mSaveBaseDirectory = file;
			return this;
		}

		public HttpManagerBuilder setTitle(String title, String baseUrl) {
			mTitle = title;
			mBaseUrl = baseUrl;
			return this;
		}

		public HttpManagerBuilder setDate(String date) {
			mTargetDate = date;
			isAutoTargetDateSet = false;
			return this;
		}

		public HttpManagerBuilder setHttpClient(CloseableHttpClient httpClient) {
			mHttpClient = httpClient;
			return this;
		}

		public HttpManager build() {
			if (mSaveBaseDirectory == null) {
				throw new IllegalStateException("保存先のディレクトリが未指定");
			}
			if (mHttpClient == null) {
				throw new IllegalStateException("HttpClientが未指定");
			}
			return new HttpManager(this);
		}
	}

	private final HttpManagerBuilder mSetting;
	private int mRequestOffset = 0;
	private File mSaveDirectory = null;

	private HttpManager(HttpManagerBuilder setting) {
		mSetting = setting;
		if (!mSetting.isAutoTargetDateSet) {
			mSaveDirectory = getSaveDirectory(mSetting.mSaveBaseDirectory,
					mSetting.mTitle, mSetting.mTargetDate);
			if (!isNotExistsFile(mSaveDirectory)) {
				throw new IllegalStateException(
						"指定したディレクトリ、ゲーム名、日付から構成したパスにファイルが存在します "
								+ mSaveDirectory.getAbsolutePath());
			}
		}
	}

	/** 指定対象のパスが存在していない、もしくはディレクトリであるかを調べる */
	private boolean isNotExistsFile(File checkTarget) {
		return (!checkTarget.exists()) ? true : (checkTarget.isDirectory());
	}

	private File getSaveDirectory(File saveBaseDirectory, String title,
			String date) {
		return new File(new File(saveBaseDirectory, title), date);
	}

	private String getUrl() {
		if (!mSetting.isAutoTargetDateSet && mRequestOffset != 0) {
			return String
					.format("https://miiverse.nintendo.net/titles/%s/hot?date=%s&offset=%d",
							mSetting.mBaseUrl, mSetting.mTargetDate,
							mRequestOffset);
		} else if (!mSetting.isAutoTargetDateSet && mRequestOffset == 0) {
			return String.format(
					"https://miiverse.nintendo.net/titles/%s/hot?date=%s",
					mSetting.mBaseUrl, mSetting.mTargetDate);
		} else if (mSetting.isAutoTargetDateSet && mRequestOffset != 0) {
			return String.format(
					"https://miiverse.nintendo.net/titles/%s/hot?offset=%d",
					mSetting.mBaseUrl, mRequestOffset);
		} else {
			return String.format("https://miiverse.nintendo.net/titles/%s/hot",
					mSetting.mBaseUrl);
		}
	}

	public int run() throws IOException {
		final CloseableHttpClient hc = mSetting.mHttpClient;
		List<IllustAttackedPostData> result = new ArrayList<>();
		while (true) {
			String requestUrl = getUrl();
			HttpUriRequest get = RequestBuilder.get().setUri(requestUrl)
					.build();
			try (CloseableHttpResponse res = hc.execute(get)) {
				String source = EntityUtils.toString(res.getEntity());
				if (mRequestOffset == 0 && mSetting.isAutoTargetDateSet) {// 最初のみ有効なエィレクトリの有無をチェック。存在していたら終了
					ZonedDateTime date = HtmlAnalyzer
							.getLastHotDateFromHtml(source);
					mSaveDirectory = getSaveDirectory(
							mSetting.mSaveBaseDirectory, mSetting.mTitle,
							String.format("%1$tF", date));
					if (mSaveDirectory.exists()) {
						System.out.println("skip " + mSetting.mTitle + "/"
								+ String.format("%1$tF", date));
						return 0;
					}
				}
				// 全てのページで実行する処理。投稿数の一覧を取得し(offset用)、画像の一覧を取得する
				List<IllustAttackedPostData> resultOne = HtmlAnalyzer
						.getPostDataListFromRawHtml(source);
				if (resultOne.size() == 0) {
					break;
				}
				resultOne.stream().filter(s -> !result.contains(s.getPostId()))
						.forEach(s -> result.add(s));
				mRequestOffset += resultOne.size();
			}
		}
		if (result.size() == 0) {
			return 0;
		}
		// イラスト付きの画像を保存する
		int index = 0;
		int saveCount = 0;
		mSaveDirectory.mkdirs();
		if (!mSaveDirectory.exists()) {
			throw new IOException("ディレクト作成に失敗:"
					+ mSaveDirectory.getAbsolutePath());
		}
		for (IllustAttackedPostData illustAttackedPostData : result) {
			index++;
			if (!illustAttackedPostData.hasIllust()) {
				continue;
			}
			HttpUriRequest get = RequestBuilder.get()
					.setUri(illustAttackedPostData.getIllustUrl()).build();
			try (CloseableHttpResponse res = hc.execute(get)) {
				try (BufferedInputStream bis = new BufferedInputStream(res
						.getEntity().getContent())) {
					File saveFile = new File(mSaveDirectory, String.format(
							"%02d-%s.png", index,
							illustAttackedPostData.getPostId()));
					try (BufferedOutputStream bos = new BufferedOutputStream(
							new FileOutputStream(saveFile))) {
						int inByte;
						while ((inByte = bis.read()) != -1) {
							bos.write(inByte);
						}
						saveCount++;
					}
				}
			}
		}
		return saveCount;
	}
}
