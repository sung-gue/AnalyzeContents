package com.breakout.server.morpheme;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.regex.Pattern;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.snu.ids.ha.index.Keyword;
import org.snu.ids.ha.index.KeywordExtractor;
import org.snu.ids.ha.index.KeywordList;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;

import com.google.gson.Gson;

/**
 * 문자열 분석 및 추출
 * 
 * @author gue
 * @since 2016. 4. 14.
 * @copyright Copyright.2016.gue.All rights reserved.
 * @version 1.0
 * @history
 *          <ol>
 *          <li>변경자/날짜 : 변경사항</li>
 *          </ol>
 */
@Controller
public class AnalyzeKkma {

    private Gson _gson;
    private KeywordExtractor _keywordExtractor;
    private Logger _logger = LoggerFactory.getLogger(AnalyzeKkma.class);

    @RequestMapping("/getKeywordKkma")
    public @ResponseBody ArrayList<String> getKeywordByManyUsed(@RequestParam(value = "contents", required = false, defaultValue = "") String contents,
            HttpServletRequest request, HttpServletResponse response, HttpSession session) throws Exception {
//        String contents = request.getParameter("contents");
        ArrayList<String> tags = new ArrayList<>();
        long startTime = System.currentTimeMillis();
        if (contents != null && contents.length() > 1) {
            KeywordList keywordList = _keywordExtractor.extractKeyword(contents, true);
            Collections.sort(keywordList, new Comparator<Keyword>() {
                @Override
                public int compare(Keyword o1, Keyword o2) {
                    return (o1.getCnt() > o2.getCnt()) ? -1 : (o1.getCnt() < o2.getCnt()) ? 1 : 0;
                }
            });
            int cnt = 0;
            for (Keyword key : keywordList) {
                if (cnt == 10) {
                    break;
                }
                //if (Pattern.compile("^[ㄱ-ㅎ가-힣a-zA-Z0-9_]{2,50}$").matcher(key.getString()).matches()) {
                    tags.add(key.getString());
                    cnt++;
                //}
            }
            
            if (tags.size() == 0) {
                keywordList = _keywordExtractor.extractKeyword(contents, false);
                Collections.sort(keywordList, new Comparator<Keyword>() {
                    @Override
                    public int compare(Keyword o1, Keyword o2) {
                        return (o1.getCnt() > o2.getCnt()) ? -1 : (o1.getCnt() < o2.getCnt()) ? 1 : 0;
                    }
                });
                cnt = 0;
                for (Keyword key : keywordList) {
                    if (cnt == 10) {
                        break;
                    }
                    if (Pattern.compile("^[ㄱ-ㅎ가-힣a-zA-Z0-9_]{2,50}$").matcher(key.getString()).matches()) {
                        tags.add(key.getString());
                        cnt++;
                    }
                }
            }
        }

        String resJson = new Gson().toJson(tags);

        _logger.info("[{}-{}({}:{}ms)] res= {}", new Object[] {
                request.getRemoteAddr(), request.getRequestURI(), request.getMethod(), (System.currentTimeMillis() - startTime), resJson
        });

        return tags;
    }

/* ************************************************************************************************
 * INFO setter, getter
 */

    /**
     * @return the {@link #_gson}
     */
    public Gson getGson() {
        return _gson;
    }

    /**
     * @param gson the {@link #_gson} to set
     */
    public void setGson(Gson gson) {
        this._gson = gson;
    }

    /**
     * @return the {@link #_keywordExtractor}
     */
    public KeywordExtractor getKeywordExtractor() {
        return _keywordExtractor;
    }

    /**
     * @param keywordExtractor the {@link #_keywordExtractor} to set
     */
    public void setKeywordExtractor(KeywordExtractor keywordExtractor) {
        this._keywordExtractor = keywordExtractor;
    }

}