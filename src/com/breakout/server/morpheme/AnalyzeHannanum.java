package com.breakout.server.morpheme;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.regex.Pattern;

import javax.servlet.ServletContext;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.DefaultResourceLoader;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.context.ServletContextAware;

import com.google.gson.Gson;

import kr.ac.kaist.swrc.jhannanum.comm.Eojeol;
import kr.ac.kaist.swrc.jhannanum.comm.Sentence;
import kr.ac.kaist.swrc.jhannanum.hannanum.Workflow;
import kr.ac.kaist.swrc.jhannanum.plugin.MajorPlugin.MorphAnalyzer.ChartMorphAnalyzer.ChartMorphAnalyzer;
import kr.ac.kaist.swrc.jhannanum.plugin.MajorPlugin.PosTagger.HmmPosTagger.HMMTagger;
import kr.ac.kaist.swrc.jhannanum.plugin.SupplementPlugin.MorphemeProcessor.UnknownMorphProcessor.UnknownProcessor;
import kr.ac.kaist.swrc.jhannanum.plugin.SupplementPlugin.PlainTextProcessor.InformalSentenceFilter.InformalSentenceFilter;
import kr.ac.kaist.swrc.jhannanum.plugin.SupplementPlugin.PlainTextProcessor.SentenceSegmentor.SentenceSegmentor;
import kr.ac.kaist.swrc.jhannanum.plugin.SupplementPlugin.PosProcessor.NounExtractor.NounExtractor;

/**
 * 문자열 분석 및 추출
 * 
 * @author gue
 * @since 2016. 4. 20.
 * @copyright Copyright.2016.gue.All rights reserved.
 * @version 1.0
 * @history
 *          <ol>
 *          <li>변경자/날짜 : 변경사항</li>
 *          </ol>
 */
@Controller
public class AnalyzeHannanum implements ServletContextAware {
    private Logger _logger = LoggerFactory.getLogger(AnalyzeHannanum.class);
    private Gson _gson;

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

    @Autowired
    private ServletContext servletContext;

    /* (non-Javadoc)
     * @see org.springframework.web.context.ServletContextAware#setServletContext(javax.servlet.ServletContext)
     */
    @Override
    public void setServletContext(ServletContext servletContext) {
        this.servletContext = servletContext;
    }

    public static Workflow workflow;

    private synchronized void initWorkflow(HttpServletRequest request) throws Exception {
        if (workflow == null) {
//            workflow = WorkflowFactory.getPredefinedWorkflow(WorkflowFactory.WORKFLOW_NOUN_EXTRACTOR);
            workflow = new Workflow(servletContext.getRealPath("/WEB-INF/hannanum_dic"));

            /* Setting up the work flow */
            /* Phase1. Supplement Plug-in for analyzing the plain text */
            workflow.appendPlainTextProcessor(new SentenceSegmentor(), null);
            workflow.appendPlainTextProcessor(new InformalSentenceFilter(), null);

            /* Phase2. Morphological Analyzer Plug-in and Supplement Plug-in for post processing */
            workflow.setMorphAnalyzer(new ChartMorphAnalyzer(), "conf/plugin/MajorPlugin/MorphAnalyzer/ChartMorphAnalyzer.json");
            workflow.appendMorphemeProcessor(new UnknownProcessor(), null);

            /* Phase3. Part Of Speech Tagger Plug-in and Supplement Plug-in for post processing */
            workflow.setPosTagger(new HMMTagger(), "conf/plugin/MajorPlugin/PosTagger/HmmPosTagger.json");

            /* For extracting nouns only, decomment the following line. */
            workflow.appendPosProcessor(new NounExtractor(), null);

            workflow.activateWorkflow(true);

            _logger.error("[{}-{}({}:initWorkflow)] init end", new Object[] {
                    request.getRemoteAddr(), request.getRequestURI(), request.getMethod()
            });
        }
    }

    @RequestMapping("/getKeyword")
    public @ResponseBody ArrayList<String> getKeywordByManyUsed(@RequestParam(value = "contents", required = false, defaultValue = "") String contents, HttpServletRequest request,
            HttpServletResponse response, HttpSession session) throws Exception {
//        String contents = request.getParameter("contents");
        ArrayList<String> tags = new ArrayList<>();
        long startTime = System.currentTimeMillis();

        if (contents != null && contents.length() > 1) {
            contents = contents.replaceAll("[^ㄱ-ㅎㅏ-ㅣ가-힣a-zA-Z0-9_\\-\\s\\.]", "");

            try {
                initWorkflow(request);
                workflow.analyze(contents);

                final HashMap<String, Integer> nounMap = new HashMap<>();
                LinkedList<Sentence> resultList = workflow.getResultOfDocument(new Sentence(0, 0, false));
                for (Sentence sentence : resultList) {
                    Eojeol[] eojeolArray = sentence.getEojeols();
                    for (int i = 0; i < eojeolArray.length; i++) {
                        if (eojeolArray[i].length > 0) {
                            String[] morphemes = eojeolArray[i].getMorphemes();
                            String str = "";
                            for (int j = 0; j < morphemes.length; j++) {
                                str += morphemes[j];
                                /*if (morphemes[j].length() > 1 && nounMap.get(morphemes[j]) != null) {
                                    nounMap.put(morphemes[j], nounMap.get(morphemes[j]) + 1);
                                }
                                else {
                                    nounMap.put(morphemes[j], 1);
                                }*/
                            }
                            // 태그 제한 : 2~50자, 숫자로만 이루어진 값
                            if (!Pattern.compile("[0-9]+").matcher("sadf").matches() &&str.length() > 1 && str.length() < 51) {
                                if (nounMap.get(str) != null) {
                                    nounMap.put(str, nounMap.get(str) + 1);
                                }
                                else {
                                    nounMap.put(str, 1);
                                }
                            }

                        }
                    }
                }

                List<String> list = new ArrayList<String>();
                list.addAll(nounMap.keySet());
                Collections.sort(list, new Comparator<String>() {
                    public int compare(String o1, String o2) {
                        int v1 = nounMap.get(o1);
                        int v2 = nounMap.get(o2);
                        return v1 > v2 ? -1 : v1 < v2 ? 1 : 0;// ((Comparable) v2).compareTo(v1);
                    }
                });

                Iterator<String> keys = list.iterator();
                int cnt = 0;
                while (keys.hasNext()) {
                    if (cnt == 10) {
                        break;
                    }
                    String key = keys.next();
                    tags.add(key);
                    cnt++;
                }
//                workflow.close();
            }
            catch (Exception e) {
                e.printStackTrace();
                _logger.error("[{}-{}({}:{}ms)] err= {}", new Object[] {
                        request.getRemoteAddr(), request.getRequestURI(), request.getMethod(), (System.currentTimeMillis() - startTime), e.getMessage()
                });
            }
//            workflow.close();
        }

        String resJson = new Gson().toJson(tags);

        _logger.info("[{}-{}({}:{}ms)] res= {}", new Object[] {
                request.getRemoteAddr(), request.getRequestURI(), request.getMethod(), (System.currentTimeMillis() - startTime), resJson
        });

        return tags;
    }
//    public @ResponseBody ArrayList<String> getKeywordByManyUsed(@RequestParam(value = "contents", required = false, defaultValue = "") String contents, HttpServletRequest request,
//            HttpServletResponse response, HttpSession session) throws Exception {
////        String contents = request.getParameter("contents");
//        ArrayList<String> tags = new ArrayList<>();
//        long startTime = System.currentTimeMillis();
//        
//        if (contents != null && contents.length() > 1) {
//            contents = contents.replaceAll("[^ㄱ-ㅎㅏ-ㅣ가-힣a-zA-Z0-9_\\-\\s\\.]", "");
//            
////            Workflow workflow = WorkflowFactory.getPredefinedWorkflow(WorkflowFactory.WORKFLOW_NOUN_EXTRACTOR);
//            Workflow workflow = new Workflow(servletContext.getRealPath("/WEB-INF/hannanum_dic"));
//            try {
//                /* Setting up the work flow */
//                /* Phase1. Supplement Plug-in for analyzing the plain text */
//                workflow.appendPlainTextProcessor(new SentenceSegmentor(), null);
//                workflow.appendPlainTextProcessor(new InformalSentenceFilter(), null);
//                
//                /* Phase2. Morphological Analyzer Plug-in and Supplement Plug-in for post processing */
//                workflow.setMorphAnalyzer(new ChartMorphAnalyzer(), "conf/plugin/MajorPlugin/MorphAnalyzer/ChartMorphAnalyzer.json");
//                workflow.appendMorphemeProcessor(new UnknownProcessor(), null);
//                
//                /* Phase3. Part Of Speech Tagger Plug-in and Supplement Plug-in for post processing */
//                workflow.setPosTagger(new HMMTagger(), "conf/plugin/MajorPlugin/PosTagger/HmmPosTagger.json");
//                
//                /* For extracting nouns only, decomment the following line. */
//                workflow.appendPosProcessor(new NounExtractor(), null);
//                
////                workflow.activateWorkflow(true);
//                workflow.activateWorkflow(false);
//                workflow.analyze(contents);
//                
//                final HashMap<String, Integer> nounMap = new HashMap<>();
//                LinkedList<Sentence> resultList = workflow.getResultOfDocument(new Sentence(0, 0, false));
//                for (Sentence sentence : resultList) {
//                    Eojeol[] eojeolArray = sentence.getEojeols();
//                    for (int i = 0; i < eojeolArray.length; i++) {
//                        if (eojeolArray[i].length > 0) {
//                            String[] morphemes = eojeolArray[i].getMorphemes();
//                            String str = "";
//                            for (int j = 0; j < morphemes.length; j++) {
//                                str += morphemes[j];
////                                if (morphemes[j].length() > 1 && nounMap.get(morphemes[j]) != null) {
////                                    nounMap.put(morphemes[j], nounMap.get(morphemes[j]) + 1);
////                                }
////                                else {
////                                    nounMap.put(morphemes[j], 1);
////                                }
//                            }
//                            if (str.length() > 1 && nounMap.get(str) != null) {
//                                nounMap.put(str, nounMap.get(str) + 1);
//                            }
//                            else {
//                                nounMap.put(str, 1);
//                            }
//                            
//                        }
//                    }
//                }
//                
//                List<String> list = new ArrayList<String>();
//                list.addAll(nounMap.keySet());
//                Collections.sort(list, new Comparator<String>() {
//                    public int compare(String o1, String o2) {
//                        int v1 = nounMap.get(o1);
//                        int v2 = nounMap.get(o2);
//                        return v1 > v2 ? -1 : v1 < v2 ? 1 : 0;// ((Comparable) v2).compareTo(v1);
//                    }
//                });
//                
//                Iterator<String> keys = list.iterator();
//                int cnt = 0;
//                while (keys.hasNext()) {
//                    if (cnt == 10) {
//                        break;
//                    }
//                    String key = keys.next();
//                    tags.add(key);
//                    cnt++;
//                }
//                workflow.close();
//            }
//            catch (Exception e) {
//                _logger.error("[{}-{}({}:{}ms)] msg= {}", new Object[] {
//                        request.getRemoteAddr(), request.getRequestURI(), request.getMethod(), (System.currentTimeMillis() - startTime), e.getMessage()
//                });
//            }
//            workflow.close();
//        }
//        
//        String resJson = new Gson().toJson(tags);
//        
//        _logger.info("[{}-{}({}:{}ms)] res= {}", new Object[] {
//                request.getRemoteAddr(), request.getRequestURI(), request.getMethod(), (System.currentTimeMillis() - startTime), resJson
//        });
//        
//        return tags;
//    }

}