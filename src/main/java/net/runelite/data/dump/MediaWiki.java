/*
 * MIT License
 *
 * Copyright (c) 2018 Tomas Slusny
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */
package net.runelite.data.dump;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.Map;
import net.runelite.data.App;
import okhttp3.HttpUrl;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

public class MediaWiki
{
	private static final class WikiInnerResponse
	{
		Map<String, String> wikitext;
	}

	private static final class WikiResponse
	{
		WikiInnerResponse parse;
	}

	private final OkHttpClient client = new OkHttpClient();
	private final HttpUrl base;

	public MediaWiki(final String base)
	{
		this.base = HttpUrl.parse(base).newBuilder().addPathSegment("api.php").build();
	}

	public String getPageData(final String page, int section)
	{
		final HttpUrl.Builder urlBuilder = base.newBuilder()
			.addQueryParameter("action", "parse")
			.addQueryParameter("format", "json")
			.addQueryParameter("prop", "wikitext")
			.addQueryParameter("redirects", "true")
			.addQueryParameter("page", page.replaceAll(" ", "_"));

		if (section != -1)
		{
			urlBuilder.addQueryParameter("section", String.valueOf(section));
		}

		final HttpUrl url = urlBuilder.build();

        final Request request = new Request.Builder()
			.url(url)
			.build();

		try(final Response response = client.newCall(request).execute())
		{
			if (response.isSuccessful())
			{
				final InputStream in = response.body().byteStream();
				return App.GSON.fromJson(new InputStreamReader(in), WikiResponse.class).parse.wikitext.get("*");
			}
		}
		catch (IOException | NullPointerException e)
		{
            return "";
		}

		return "";
	}
}
