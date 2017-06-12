package com.byes.ignition.client.utils;

import com.inductiveautomation.ignition.common.BasicDataset;
import com.inductiveautomation.ignition.common.BundleUtil;
import com.inductiveautomation.ignition.common.execution.impl.BasicExecutionEngine;
import com.inductiveautomation.ignition.common.model.values.QualifiedValue;
import com.inductiveautomation.ignition.common.script.builtin.KeywordArgs;
import com.inductiveautomation.ignition.common.script.builtin.PyArgumentMap;
import com.inductiveautomation.ignition.common.script.hints.NoHint;
import com.inductiveautomation.ignition.common.script.hints.ScriptArg;
import com.inductiveautomation.ignition.common.script.hints.ScriptFunction;
import com.inductiveautomation.ignition.common.sqltags.model.Tag;
import com.inductiveautomation.ignition.common.sqltags.model.TagPath;
import com.inductiveautomation.ignition.common.sqltags.model.TagProp;
import com.inductiveautomation.ignition.common.sqltags.model.event.TagChangeEvent;
import com.inductiveautomation.ignition.common.sqltags.model.event.TagChangeListener;
import com.inductiveautomation.ignition.common.sqltags.model.types.TagType;
import com.inductiveautomation.ignition.common.sqltags.parser.TagPathParser;

import org.python.core.PyObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.List;
import java.util.HashMap;
import java.util.Map;
import java.util.ArrayList;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

import com.inductiveautomation.ignition.client.model.ClientContext;

public class ClientScriptModule{
    static {
        BundleUtil.get().addBundle(
                ClientScriptModule.class.getSimpleName(),
                ClientScriptModule.class.getClassLoader(),
                ClientScriptModule.class.getName().replace('.', '/'));
    }

    private static Pattern pattern = null;
    private static Matcher matcher = null;

    // the dataset client tag to create in ignition to be notified of tags data subscriptions
    private final String TAGPATH_CLIENT_DATASET = "[Client]tag_subscription";
    private final Integer UPDATE_FREQUENCY_MS = 250;
    private final String EXECUTION_ENGINE_NAME = "CyclicUpdateTagClientDataset";

    private final Logger logger = LoggerFactory.getLogger(getClass());

    private ClientContext clientContext;

    private Map<TagPath,TagChangeListener> paths = new HashMap<TagPath,TagChangeListener>();
    private ConcurrentHashMap<String,HashMap> mapTags = new ConcurrentHashMap<String,HashMap>();

    private List<String> listFreezedSubscribedTagPath = new ArrayList<String>();

    private List<String> listNomColonne = new ArrayList<String>();
    private List<Class<?>> listTypeColonne = new ArrayList<Class<?>>();

    // flag to ask for update of the tag client dataset
    private AtomicBoolean updateTagClient = new AtomicBoolean(false);

    private BasicExecutionEngine executionEngine = null;
    private ExecutionCyclic executionCyclic = null;

    private boolean flagRegistered = false;

    private boolean browseLimitReached = false;

    public ClientScriptModule(ClientContext _clientContext) {
        try {
            logger.debug("Constructor ClientScriptModule");

            this.clientContext = _clientContext;

            listNomColonne.add("TagFullPath");
            listTypeColonne.add(String.class);

            listNomColonne.add("Value");
            listTypeColonne.add(String.class);

            listNomColonne.add("LastChange");
            listTypeColonne.add(java.util.Date.class);

            listNomColonne.add("Quality");
            listTypeColonne.add(String.class);

            listNomColonne.add("DataType");
            listTypeColonne.add(String.class);

            listNomColonne.add("EngHigh");
            listTypeColonne.add(Double.class);

            listNomColonne.add("EngLow");
            listTypeColonne.add(Double.class);

            listNomColonne.add("Documentation");
            listTypeColonne.add(String.class);

            listNomColonne.add("Tooltip");
            listTypeColonne.add(String.class);

            listNomColonne.add("OPCServer");
            listTypeColonne.add(String.class);

            listNomColonne.add("OPCItemPath");
            listTypeColonne.add(String.class);

            listNomColonne.add("ScanClass");
            listTypeColonne.add(String.class);

            listNomColonne.add("AccessRights");
            listTypeColonne.add(String.class);

        } catch (Exception e) {
            logger.error("error : ",e);
        }
    }

    /**
     * Recursive Method
     * @param fullTagPath : full path with provider for a tag or a folder
     * @param resultListFullTagPath
     * @param limitResults
     * @return : false if ok, true si result was truncated or browse error
     * Caused by: com.inductiveautomation.ignition.client.gateway_interface.GatewayException: The tag provider 'xxxx' is not currently available.
     */

    @NoHint
    private boolean browseTag(String fullTagPath,List<String> resultListFullTagPath,Integer limitResults){
        List<Tag> listeBr = new ArrayList<Tag>();

        if ((limitResults > 0) && (resultListFullTagPath.size() >= limitResults)){
            return true;
        }

        TagPath tagPath = null;
        try {

            tagPath = TagPathParser.parseSafe("default",fullTagPath);
            if (tagPath != null) {
                listeBr = clientContext.getTagManager().browse(tagPath);
                if (listeBr != null) {
                    for (Tag tag : listeBr) {
                        // Folder || UDT_DEF || UDT_INST
                        if (tag.getType().hasSubTags()) {
                            browseTag(fullTagPath + "/" + tag.getName(), resultListFullTagPath,limitResults);
                        } else if (tag.getType().toString().equalsIgnoreCase("OPC")
                                || (tag.getType().toString().equalsIgnoreCase("DB"))) {
                            if ((limitResults > 0) && (resultListFullTagPath.size() >= limitResults)){
                                return true;
                            }else{
                                if (pattern!=null){
                                    matcher = pattern.matcher(fullTagPath + "/" + tag.getName());
                                    if (matcher.find()){
                                        resultListFullTagPath.add(fullTagPath + "/" + tag.getName());
                                    }
                                } else {
                                    resultListFullTagPath.add(fullTagPath + "/" + tag.getName());
                                }
                            }
                        }
                    }
                } else {
                    // we assume it is a tag path
                    resultListFullTagPath.add(tagPath.toStringFull());
                }
            }
            return false;
        } catch (Exception e) {
            logger.error("error : ",e);
            return true;
        }
    }

    @ScriptFunction(docBundlePrefix = "ClientScriptModule")
    @KeywordArgs(names = {"tagpaths","limit","regex"}, types = {List.class,Integer.class,String.class})
    public synchronized List<String> browse(PyObject[] pyArgs, String[] keywords) {
        List<String> resultListFullTagPath = new ArrayList<String>();
        try {
            PyArgumentMap args = PyArgumentMap.interpretPyArgs(pyArgs, keywords, ClientScriptModule.class, "browse");

            List<java.lang.String> listTagPath = (List<String>) args.getArg("tagpaths",null);
            Integer limitResults = args.getIntArg("limit",0);
            String regex = args.getStringArg("regex","");

            if (listTagPath != null) logger.debug(listTagPath.toString());
            logger.debug("limitResults={}",limitResults);
            logger.debug("regex={}",regex);
            if (!regex.isEmpty()){
                try{
                    pattern = Pattern.compile(regex);
                } catch (PatternSyntaxException e){
                    logger.error("error : ",e);
                    pattern = null;
                }
            } else {
                pattern = null;
            }

            for (String tagPath : listTagPath){
                browseLimitReached = browseTag(tagPath,resultListFullTagPath,limitResults);
            }
        } catch (Exception e) {
            logger.error("error : ",e);
        }
        return resultListFullTagPath;
    }

    @ScriptFunction(docBundlePrefix = "ClientScriptModule")
    public synchronized boolean isBrowseLimitReached() {
        return browseLimitReached;
    }

    @ScriptFunction(docBundlePrefix = "ClientScriptModule")
    public synchronized int getSizeOfSubscribedTagsList() {
        if (paths==null){
            return 0;
        }else{
            return paths.size();
        }
    }

    @ScriptFunction(docBundlePrefix = "ClientScriptModule")
    public synchronized List<String> getSubscribedFullTagPathsList() {
        List<String> listFullTagPath = new ArrayList<String>();
        if (paths != null){
            for (TagPath tagpath : paths.keySet()){
                listFullTagPath.add(tagpath.toStringFull());
            }
        }
        return(listFullTagPath);
    }

    @ScriptFunction(docBundlePrefix = "ClientScriptModule")
    public synchronized List<TagPath> getSubscribedTagPathsList() {
        List<TagPath> listTagPath;
        if (paths != null){
            listTagPath = new ArrayList<TagPath>(paths.keySet());
        } else {
            listTagPath = new ArrayList<TagPath>();
        }
        return(listTagPath);
    }

    @ScriptFunction(docBundlePrefix = "ClientScriptModule")
    public void subscribe(@ScriptArg("listTagPath") java.util.List<java.lang.String> listTagPath) {
        try {
            if (checkTagClientExist()==false){
                logger.error("You Must create tag client for subscription update : {}",TAGPATH_CLIENT_DATASET);
            }

            if (listFreezedSubscribedTagPath!=null) listFreezedSubscribedTagPath.clear();

            List<TagPath> listAdd = new ArrayList<TagPath>();
            List<TagChangeListener> listenersAdd = new ArrayList<TagChangeListener>();

            // Subscription for property Value => see MyTagChangeListener
            for (String fullTagPath : listTagPath){
                TagPath tagPath;
                tagPath = TagPathParser.parseSafe("default",fullTagPath);
                if (tagPath!=null){
                    if (!paths.containsKey(tagPath)){
                        listAdd.add(tagPath);
                        // TODO : possibly use the same tag change listener (synchronized) for all tag ???
                        MyTagChangeListener listener = new MyTagChangeListener();
                        listenersAdd.add(listener);
                        paths.put(tagPath,listener);
                    }
                }else{
                    logger.error("parseSafe for : {} return null",fullTagPath);
                }
            }

            if (!listAdd.isEmpty()){
                // Read properties and VQT of added tags data to update the client dataset
                List<Tag> listTag = this.clientContext.getTagManager().getTags(listAdd);
                logger.debug("listTag.size()=" + listTag.size());

                int indexPaths = 0;
                for (Tag tag : listTag) {
                    HashMap mapValue = new HashMap();
                    String TagFullPath = listAdd.get(indexPaths).toStringFull();
                    logger.debug("TagFullPath=" + TagFullPath);
                    mapValue.put("TagFullPath",TagFullPath);
                    // the value is casted to a String
                    mapValue.put("Value", (tag.getValue().getValue() == null) ? "null" : tag.getValue().getValue().toString());
                    mapValue.put("LastChange",tag.getValue().getTimestamp());
                    mapValue.put("Quality",tag.getValue().getQuality().toString());
                    mapValue.put("DataType",tag.getDataType().toString());
                    mapValue.put("EngHigh",tag.getAttribute(TagProp.EngHigh).getValue());
                    mapValue.put("EngLow",tag.getAttribute(TagProp.EngLow).getValue());
                    mapValue.put("Documentation",tag.getAttribute(TagProp.Documentation).getValue());
                    mapValue.put("Tooltip",tag.getAttribute(TagProp.Tooltip).getValue());
                    if (tag.getType().equals(TagType.OPC)){
                        mapValue.put("OPCServer",tag.getAttribute(TagProp.OPCServer).getValue());
                        mapValue.put("OPCItemPath",tag.getAttribute(TagProp.OPCItemPath).getValue());
                    }else{
                        mapValue.put("OPCServer","");
                        mapValue.put("OPCItemPath","");
                    }
                    mapValue.put("ScanClass",tag.getAttribute(TagProp.ScanClass).getValue());
                    mapValue.put("AccessRights",tag.getAttribute(TagProp.AccessRights).getValue().toString());
                    mapTags.put(TagFullPath, mapValue);
                    logger.debug("mapTags.put tagpath {} value {}",listAdd.get(indexPaths).toStringFull(),mapValue.toString());
                    indexPaths++;
                }

                // Subsciption for added tags
                this.clientContext.getTagManager().subscribe(listAdd,listenersAdd);
                logger.debug("Add subscriptions for VTQ of {} tags",paths.size());

                // create if first time
                if (executionEngine==null){
                    executionEngine = new BasicExecutionEngine();
                }
                startCyclicUpdateTagClientDataset();
                updateTagClientDataset();
            } else {
                logger.debug("No subscriptions to add");
            }
        } catch (Exception e) {
            logger.error("error : ",e);
        }
    }

    @ScriptFunction(docBundlePrefix = "ClientScriptModule")
    public void unsubscribe(@ScriptArg("listTagPath") java.util.List<java.lang.String> listFullTagPath) {
        try {
            if (listFullTagPath==null){
                return;
            }
            List<TagPath> listTagPath = new ArrayList<TagPath>();
            List<TagChangeListener> listTagChangeListener = new ArrayList<TagChangeListener>();
            for (String fullTagPath : listFullTagPath){
                TagPath tagPath = TagPathParser.parseSafe("default",fullTagPath);
                if (tagPath!=null) {
                    TagChangeListener tagChangeListener = paths.get(tagPath);
                    if (tagChangeListener != null){
                        listTagPath.add(tagPath);
                        listTagChangeListener.add(tagChangeListener);
                        paths.remove(tagPath);
                        mapTags.remove(fullTagPath);
                    }
                }
            }
            this.clientContext.getTagManager().unsubscribe(listTagPath,listTagChangeListener);
            logger.debug("Delete subscriptions for VTQ of {} tags",listTagPath.size());
            updateTagClientDataset();
        } catch (Exception e) {
            logger.error("error : ",e);
        }
    }

    @ScriptFunction(docBundlePrefix = "ClientScriptModule")
    public void unsubscribeAll() {
        try {
            List<TagPath> listTagPath = new ArrayList<TagPath>();
            List<TagChangeListener> listTagChangeListener = new ArrayList<TagChangeListener>();
            for (HashMap.Entry<TagPath, TagChangeListener> entry : paths.entrySet()) {
                listTagPath.add(entry.getKey());
                listTagChangeListener.add(entry.getValue());
            }
            this.clientContext.getTagManager().unsubscribe(listTagPath,listTagChangeListener);
            logger.debug("Delete subscriptions for VTQ of {} tags",listTagPath.size());
            paths.clear();
            mapTags.clear();
            updateTagClientDataset();
            listFreezedSubscribedTagPath.clear();
        } catch (Exception e) {
            logger.error("error : ",e);
        }
    }

    @ScriptFunction(docBundlePrefix = "ClientScriptModuleDataset")
    public synchronized void freezeAll() {
        try {
            logger.debug("freezeAll()");
            listFreezedSubscribedTagPath.clear();
            List<TagPath> listTagPath = new ArrayList<TagPath>();
            List<TagChangeListener> listTagChangeListener = new ArrayList<TagChangeListener>();
            for (HashMap.Entry<TagPath, TagChangeListener> entry : paths.entrySet()) {
                listTagPath.add(entry.getKey());
                listTagChangeListener.add(entry.getValue());
                // Store subscribed tags
                listFreezedSubscribedTagPath.add(entry.getKey().toStringFull());
            }
            logger.debug("Save of {} tags",listFreezedSubscribedTagPath.size());
            this.clientContext.getTagManager().unsubscribe(listTagPath,listTagChangeListener);
            logger.debug("Delete subscriptions for VTQ of {} tags",listTagPath.size());
            paths.clear();
            mapTags.clear();
            updateTagClientDataset();
        } catch (Exception e) {
            logger.error("error : ",e);
        }
    }

    @ScriptFunction(docBundlePrefix = "ClientScriptModuleDataset")
    public void unfreezeAll() {
        try {
            logger.debug("unfreezeAll()");
            if (listFreezedSubscribedTagPath != null){
                if (!listFreezedSubscribedTagPath.isEmpty()) {
                    subscribe(new ArrayList<String>(listFreezedSubscribedTagPath));
                }
            }
        } catch (Exception e) {
            logger.error("error : ",e);
        }
    }

    @ScriptFunction(docBundlePrefix = "ClientScriptModule")
    public synchronized void forceUpdateTagClientDataset(){
        try {
            if (!mapTags.isEmpty()){
                List<TagPath> listTagRead = new ArrayList<TagPath>(paths.keySet());
                try{
                    // /!\ bug Ignition => getTagManager().read doesen't work in client scope
                    // com.inductiveautomation.ignition.client.gateway_interface.GatewayException: Unable to locate function 'SQLTags.read'
                    // See Old forum post https://inductiveautomation.com/forum/viewtopic.php?f=74&t=16733
                    // New forum : https://forum.inductiveautomation.com/t/client-module-with-sql-tag-read/12699
                    //List<QualifiedValue> listQv = this.clientContext.getTagManager().read(listTagRead);
                    List<Tag> listTag = this.clientContext.getTagManager().getTags(listTagRead);
                    int indexTag = 0;
                    // the results list of tags read is ordered according to the tag to read
                    for (Tag tag : listTag){
                        HashMap mapValue = mapTags.get(listTagRead.get(indexTag).toStringFull());
                        if (mapValue != null) {
                            mapValue.put("Value", Utils.tagValueToString(tag));
                            mapValue.put("LastChange", tag.getValue().getTimestamp());
                            mapValue.put("Quality", tag.getValue().getQuality().toString());
                        } else {
                            logger.error("forceUpdateTagClientDataset tagFullPath not found in mapTags : {}", listTagRead.get(indexTag).toStringFull());
                        }
                        indexTag ++;
                    }
                }catch(Exception e){
                    logger.error("error : ",e);
                }
            }
            updateTagClientDataset();
        }catch(Exception e){
            logger.error("error : ",e);
        }
    }

    @NoHint
    private boolean checkTagClientExist(){
        List<TagPath> listRead = new ArrayList<TagPath>();
        try {
            listRead.add(TagPathParser.parse("client", TAGPATH_CLIENT_DATASET));
            List<QualifiedValue> listQv = this.clientContext.getTagManager().read(listRead);
            if (listQv.isEmpty()==false){
                return true;
            } else {
                return false;
            }
        } catch (IOException e) {
            logger.error("error : ",e);
            return false;
        }
    }

    private class ExecutionCyclic implements Runnable{
        @Override
        public void run() {
            logger.trace("Execute CyclicUpdateTagClientDataset");
            if (updateTagClient.compareAndSet(true,false)) {
                updateTagClientDataset();
            }
        }
    }

    @NoHint
    private void startCyclicUpdateTagClientDataset(){
        if (executionEngine != null){
            if (executionCyclic == null){
                executionCyclic = new ExecutionCyclic();
            }
            logger.debug("BasicExecutionEngine started @Rate={} Ms",UPDATE_FREQUENCY_MS);
            executionEngine.register("Byes-Utils-Module",EXECUTION_ENGINE_NAME,executionCyclic,UPDATE_FREQUENCY_MS, TimeUnit.MILLISECONDS);
            flagRegistered = true;
        } else {
            logger.error("BasicExecutionEngine is null => create BasicExecutionEngine before starting it");
        }
    }

    @NoHint
    public void stopCyclicUpdateTagClientDataset(){
        if (executionEngine != null){
            if (flagRegistered == true) {
                executionEngine.unRegister("Byes-Utils-Module",EXECUTION_ENGINE_NAME);
                logger.debug("BasicExecutionEngine stopped");
                flagRegistered = false;
            }
        }
    }

    @NoHint
    public void shutdownCyclicUpdateTagClientDataset(){
        unsubscribeAll();
        if (executionEngine != null){
            executionEngine.shutdown();
            logger.debug("BasicExecutionEngine shutdown");
        }
    }

    @NoHint
    private void updateTagClientDataset(){
        try {
            /*
                public BasicDataset(java.util.List<java.lang.String> columnNames,
                        java.util.List<java.lang.Class<?>> columnTypes,
                        java.lang.Object[][] data)
                Constructor that takes all of the information needed to create a populated dataset.
                        Parameters:
                columnNames - The column names of the dataset. Must match the length of columnTypes and data.length
                columnTypes - The types of each column. Must match the length of columnNames and data.length
                data - The raw data. An array of columns of data. (NOT rows.)
            */

            if (mapTags.isEmpty()){
                // Write a null dataset
                this.clientContext.getTagManager().write(TagPathParser.parse("client", TAGPATH_CLIENT_DATASET), null);
                stopCyclicUpdateTagClientDataset();
            } else {
                Object[][] data = new Object[listNomColonne.size()][mapTags.keySet().size()];
                // Convert mapTags to Dataset
                int ligne = 0;
                for (HashMap.Entry<String, HashMap> entry : mapTags.entrySet()) {
                    HashMap mapValues = entry.getValue();
                    int colonne = 0;
                    for (String colonneName : listNomColonne){
                        data[colonne][ligne] = mapValues.get(colonneName);
                        colonne++;
                    }
                    ligne++;
                }
                // Write the dataset in the tag client
                BasicDataset dataset = new BasicDataset(listNomColonne, listTypeColonne, data);
                this.clientContext.getTagManager().write(TagPathParser.parse("client", TAGPATH_CLIENT_DATASET), dataset);
            }
        }catch(Exception e){
            logger.error("error : ",e);
        }
    }

    private class MyTagChangeListener implements TagChangeListener {
        // Nota : first event on subxcription is not filtered
        public MyTagChangeListener(){
            super();
        }

        @Override
        public void tagChanged(TagChangeEvent e)
        {
            try{
                logger.debug("tagChanged : FullTagPath={}, Value={}, LastChange={}", e.getTagPath().toStringFull()
                                                                    , (e.getTag().getValue().getValue() == null) ? "null" : e.getTag().getValue().getValue().toString()
                                                                    , e.getTag().getValue().getTimestamp().toString());
                // e.getTagPath().toStringFull() => [source]path/to/tag
                HashMap mapValue = mapTags.get(e.getTagPath().toStringFull());
                if (mapValue != null) {
                    mapValue.put("Value", Utils.tagValueToString(e.getTag()));
                    mapValue.put("LastChange", e.getTag().getValue().getTimestamp());
                    mapValue.put("Quality", e.getTag().getValue().getQuality().toString());
                    updateTagClient.set(true);
                } else {
                    logger.error("tag changed, not found : {}", e.getTagPath().toStringFull());
                }
            }catch(Exception ex){
                logger.error(ex.getMessage());
            }
        }
        public TagProp getTagProperty()
        {
            // mention the property change to monitor
            // null => all property change
            //return (TagProp.Value); => only a specific property
            return null;
        }
    }

    /**
     * Check if a list of tag exists
     * @param listFullTagPath : List of Full tag path to check
     * @return list of Boolean (True if tag exist)
     */
    @ScriptFunction(docBundlePrefix = "ClientScriptModule")
    public List<Boolean> tagsExists(List<String> listFullTagPath){
        List<Boolean> results = new ArrayList<Boolean>();
        List<TagPath> listTagPath = new ArrayList<TagPath>();
        try {
            // String Full tag path to tagPath
            for (String fullTagPath : listFullTagPath) {
                try {
                    TagPath tagPath;
                    tagPath = TagPathParser.parseSafe("default", fullTagPath);
                    if (tagPath != null) {
                        listTagPath.add(tagPath);
                    } else {
                        listTagPath.add(null);
                        logger.error("parseSafe for : {} return null", fullTagPath);
                    }
                }catch(Exception e){
                    listTagPath.add(null);
                    logger.error("error : ",e);
                }
            }
            // /!\ bug Ignition => fonction ne marche pas dans le contexte client
            // com.inductiveautomation.ignition.client.gateway_interface.GatewayException: Unable to locate function 'SQLTags.read'
            // cf post https://inductiveautomation.com/forum/viewtopic.php?f=74&t=16733
            //List<QualifiedValue> listQv = this.clientContext.getTagManager().read(listTagRead);
            List<Tag> listTag = this.clientContext.getTagManager().getTags(listTagPath);
            for (Tag tag : listTag){
                if (tag != null) {
                    results.add(Boolean.TRUE);
                } else{
                    results.add(Boolean.FALSE);
                }
            }
        }catch(Exception e){
            logger.error("error : ",e);
        }
        return results;
    }
}