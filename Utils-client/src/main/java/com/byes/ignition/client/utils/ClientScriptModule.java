package com.byes.ignition.client.utils;

import com.inductiveautomation.factorypmi.application.script.builtin.ClientSystemUtilities;
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
import com.inductiveautomation.ignition.common.sqltags.model.types.DataType;
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

    // e.getStackTrace()[0].getMethodName() : a tester pour log method

    private static Pattern pattern = null;
    private static Matcher matcher = null;

    private final String TAGPATH_CLIENT_DATASET = "[Client]tag_subscription";
    private final Integer UPDATE_FREQUENCY_MS = 250;
    private final String EXECUTION_ENGINE_NAME = "CyclicUpdateTagClientDataset";

    private final Logger logger = LoggerFactory.getLogger(getClass());

    private ClientContext clientContext;

    private Map<TagPath,TagChangeListener> paths = new HashMap<TagPath,TagChangeListener>();
    private ConcurrentHashMap<String,HashMap> mapTags = new ConcurrentHashMap<String,HashMap>();

    //1.0.4
    private List<String> listFreezedSubscribedTagPath = new ArrayList<String>();

    private List<String> listNomColonne = new ArrayList<String>();
    private List<Class<?>> listTypeColonne = new ArrayList<Class<?>>();

    // flag de demande update cyclique du dataset client
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
     * Methode récursive de parcours des Tags ignition
     */
    // fullTagPath => path complet d'un tag ou d'un folder
    // return true, si result limité ou erreur lors du browse
    // TODO : A revoir CR int ?
    // Caused by: com.inductiveautomation.ignition.client.gateway_interface.GatewayException: The tag provider 'SITE1' is not currently available.

    @NoHint
    private boolean browseTag(String fullTagPath,List<String> resultListFullTagPath,Integer limitResults){
        List<Tag> listeBr = new ArrayList<Tag>();

        if ((limitResults > 0) && (resultListFullTagPath.size() >= limitResults)){
            return true;
        }

        TagPath tagPath = null;
        try {
            // fonction sans le null nok
            //tagPath = TagPathParser.parse("default",null,fullTagPath);
            tagPath = TagPathParser.parseSafe("default",fullTagPath);
            // voir comment tester si déjà un tag ???? et pas la path d'un folder
            if (tagPath != null) {
                //logger.info("parseSafe : {}",tagPath.getItemName());
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
                                // retient dont le path matche avec la regex
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
                    // on suppose que c'était le path d'un tag
                    resultListFullTagPath.add(tagPath.toStringFull());
                }
            }
            return false;
        } catch (Exception e) {
            logger.error("error : ",e);
            return true;
        }
    }

    // Fonction initiale sans gestion des args par keywords
    //    public List<String> browse(@ScriptArg("listTagPath") java.util.List<java.lang.String> listTagPath,
    //@ScriptArg("limitResults") Integer limitResults) {

    @ScriptFunction(docBundlePrefix = "ClientScriptModule")
    @KeywordArgs(names = {"tagpaths","limit","regex"}, types = {List.class,Integer.class,String.class})
    public List<String> browse(PyObject[] pyArgs, String[] keywords) {
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
    public boolean isBrowseLimitReached() {
        return browseLimitReached;
    }

    @ScriptFunction(docBundlePrefix = "ClientScriptModule")
    public int getSizeOfSubscribedTagsList() {
        if (paths==null){
            return 0;
        }else{
            return paths.size();
        }
    }

    //1.0.4
    // Nota : liste peut être trié différamment selon le keySet
    @ScriptFunction(docBundlePrefix = "ClientScriptModule")
    public List<String> getSubscribedFullTagPathsList() {
        List<String> listFullTagPath = new ArrayList<String>();
        if (paths != null){
            for (TagPath tagpath : paths.keySet()){
                listFullTagPath.add(tagpath.toStringFull());
            }
        }
        return(listFullTagPath);
    }

    //1.0.4
    @ScriptFunction(docBundlePrefix = "ClientScriptModule")
    public List<TagPath> getSubscribedTagPathsList() {
        List<TagPath> listTagPath;
        if (paths != null){
            listTagPath = new ArrayList<TagPath>(paths.keySet());
        } else {
            listTagPath = new ArrayList<TagPath>();
        }
        return(listTagPath);
    }

    /**
     * Abonnement de la liste de tag, ajout aux abonnements existant
     * @param listTagPath : List des tagPath à souscrire (yc provider [...])
     */
    @ScriptFunction(docBundlePrefix = "ClientScriptModule")
    public void subscribe(@ScriptArg("listTagPath") java.util.List<java.lang.String> listTagPath) {
        try {
            if (checkTagClientExist()==false){
                logger.error("You Must create tag client for subscription update : {}",TAGPATH_CLIENT_DATASET);
            }

            // Effacement systématique en cas de rappel de la fonction unfreezeAll sans freezeAll avant
            if (listFreezedSubscribedTagPath!=null) listFreezedSubscribedTagPath.clear();

            List<TagPath> listAdd = new ArrayList<TagPath>();
            List<TagChangeListener> listenersAdd = new ArrayList<TagChangeListener>();

            // Abonnement sur la propriété Value => cf MyTagChangeListener
            for (String fullTagPath : listTagPath){
                // fonction sans le null nok
                //TagPath tagPath = TagPathParser.parse("default",null,fullTagPath);
                TagPath tagPath;
                tagPath = TagPathParser.parseSafe("default",fullTagPath);
                if (tagPath!=null){
                    if (!paths.containsKey(tagPath)){
                        listAdd.add(tagPath);
                        //logger.info("add " + tagPath.toStringFull());
                        // TODO : voir si utiliser le même listener ou un par tag ??? ou gestion synchronised ???
                        MyTagChangeListener listener = new MyTagChangeListener();
                        listenersAdd.add(listener);
                        paths.put(tagPath,listener);
                    }
                }else{
                    logger.error("parseSafe for : {} return null",fullTagPath);
                }
            }

            if (!listAdd.isEmpty()){

                // lecture des données des Tag ajoutés pour les mettre dans le tag client dataset
                List<Tag> listTag = this.clientContext.getTagManager().getTags(listAdd);
                logger.debug("listTag.size()=" + listTag.size());

                int indexPaths = 0;
                for (Tag tag : listTag) {
                    HashMap mapValue = new HashMap();
                    // Valeur convertie en String
                    // TODO : revoir pour les autres types de valeurs

                    String TagFullPath = listAdd.get(indexPaths).toStringFull();
                    logger.debug("TagFullPath=" + TagFullPath);
                    mapValue.put("TagFullPath",TagFullPath);
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

                    // A completer selon le besoin
                    // ... penser à mettre à jour les listes listNomColonne et listTypeColonne dans le constructeur
                    mapTags.put(TagFullPath, mapValue);
                    logger.debug("mapTags.put tagpath {} value {}",listAdd.get(indexPaths).toStringFull(),mapValue.toString());
                    indexPaths++;
                }

                // Abonnement des tag ajoutés
                this.clientContext.getTagManager().subscribe(listAdd,listenersAdd);
                logger.debug("Add subscriptions for VTQ of {} tags",paths.size());

                // création la première fois
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
            // Attention liste triée différamment de celle des souscription
            // si utilisation d'un Set par exemple
            // => ne desabonne pas !!!
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

    //1.0.4
    @ScriptFunction(docBundlePrefix = "ClientScriptModuleDataset")
    public void freezeAll() {
        try {
            listFreezedSubscribedTagPath.clear();
            List<TagPath> listTagPath = new ArrayList<TagPath>();
            List<TagChangeListener> listTagChangeListener = new ArrayList<TagChangeListener>();
            for (HashMap.Entry<TagPath, TagChangeListener> entry : paths.entrySet()) {
                listTagPath.add(entry.getKey());
                listTagChangeListener.add(entry.getValue());
                // Mémorisation des tag souscrits
                listFreezedSubscribedTagPath.add(entry.getKey().toStringFull());
            }
            this.clientContext.getTagManager().unsubscribe(listTagPath,listTagChangeListener);
            logger.debug("Delete subscriptions for VTQ of {} tags",listTagPath.size());
            paths.clear();
            mapTags.clear();
            // nota : effacement dataset provoque RAS tag client et arrêt fonction cyclique regroupement maj dataset si besoin
            updateTagClientDataset();
        } catch (Exception e) {
            logger.error("error : ",e);
        }
    }

    //1.0.4
    @ScriptFunction(docBundlePrefix = "ClientScriptModuleDataset")
    public void unfreezeAll() {
        try {
            if (listFreezedSubscribedTagPath != null){
                if (!listFreezedSubscribedTagPath.isEmpty()) {
                    // Copie de la liste passée en paramètre car effecament systématique de listFreezedSubscribedTagPath
                    // dans subscribe
                    subscribe(new ArrayList<String>(listFreezedSubscribedTagPath));
                }
            }
        } catch (Exception e) {
            logger.error("error : ",e);
        }
    }

    // force la relecture de l'ensemble des tags
    // et la maj du dataset client
    @ScriptFunction(docBundlePrefix = "ClientScriptModule")
    public void forceUpdateTagClientDataset(){
        try {
            if (!mapTags.isEmpty()){
                List<TagPath> listTagRead = new ArrayList<TagPath>(paths.keySet());
                try{
                    // /!\ bug Ignition => fonction ne marche pas dans le contexte client
                    // com.inductiveautomation.ignition.client.gateway_interface.GatewayException: Unable to locate function 'SQLTags.read'
                    // cf post https://inductiveautomation.com/forum/viewtopic.php?f=74&t=16733
                    //List<QualifiedValue> listQv = this.clientContext.getTagManager().read(listTagRead);
                    List<Tag> listTag = this.clientContext.getTagManager().getTags(listTagRead);
                    int indexTag = 0;
                    // la liste des résultats de la lecture est ordonnée selon la liste des tag à lire
                    for (Tag tag : listTag){
                        HashMap mapValue = mapTags.get(listTagRead.get(indexTag).toStringFull());
                        if (mapValue != null) {
                            // 1.0.2 : Support des tags de type Array
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
            logger.debug("Execute CyclicUpdateTagClientDataset");

            // updateTagClient.compareAndSet(expect, update)
            // expect => valeur attendue
            // update => valeur de mise à jour si valeur attendue
            // return : true => si maj faite
            // permet de se prémunir si le flag est mis à 1 autre part
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
        if (executionEngine != null){
            executionEngine.shutdown();
            logger.debug("BasicExecutionEngine shutdown");
        }
    }

    @NoHint
    private void updateTagClientDataset(){
        try {

//            public BasicDataset(java.util.List<java.lang.String> columnNames,
//                    java.util.List<java.lang.Class<?>> columnTypes,
//                    java.lang.Object[][] data)
//            Constructor that takes all of the information needed to create a populated dataset.
//                    Parameters:
//            columnNames - The column names of the dataset. Must match the length of columnTypes and data.length
//            columnTypes - The types of each column. Must match the length of columnNames and data.length
//            data - The raw data. An array of columns of data. (NOT rows.)
//            colonne => tableau de données de même type
//            BasicDataset dataset = new BasicDataset(listNomColonne.toArray(),listTypeColonne.toArray(),null);

            if (mapTags.isEmpty()){
                // Ecriture du dataset NULL dans le tag client
                this.clientContext.getTagManager().write(TagPathParser.parse("client", TAGPATH_CLIENT_DATASET), null);
                stopCyclicUpdateTagClientDataset();
            } else {
                Object[][] data = new Object[listNomColonne.size()][mapTags.keySet().size()];
                // Conversion mapTags en Dataset
                int ligne = 0;
                // parcours de remplissage de data par ligne
                for (HashMap.Entry<String, HashMap> entry : mapTags.entrySet()) {
                    HashMap mapValues = entry.getValue();
                    int colonne = 0;
                    for (String colonneName : listNomColonne){
                        data[colonne][ligne] = mapValues.get(colonneName);
                        colonne++;
                    }
                    ligne++;
                }
                // Ecriture du dataset dans le tag client
                BasicDataset dataset = new BasicDataset(listNomColonne, listTypeColonne, data);
                this.clientContext.getTagManager().write(TagPathParser.parse("client", TAGPATH_CLIENT_DATASET), dataset);
            }
        }catch(Exception e){
            logger.error("error : ",e);
        }
    }

    private class MyTagChangeListener implements TagChangeListener {
        // Nota : pas de filtrage du premier évènement qui permet de mettre à jour la première valeur

        public MyTagChangeListener(){
            super();
        }

        @Override
        public void tagChanged(TagChangeEvent e)
        {
            try{
/*
                if (e.getTagProperty()==null){
                    logger.info("tagChanged : e.getTagProperty()==null");
                } else if (e.getTagProperty().equals(TagProp.Value)) {

                }*/

                logger.debug("tagChanged : FullTagPath={}, Value={}, LastChange={}", e.getTagPath().toStringFull()
                                                                    , (e.getTag().getValue().getValue() == null) ? "null" : e.getTag().getValue().getValue().toString()
                                                                    , e.getTag().getValue().getTimestamp().toString());
                // TODO : toStringFull voir si retourne le bon path, deja avec provider ?????
                // e.getTagPath().toStringFull() => de la form [source]path/to/tag

                HashMap mapValue = mapTags.get(e.getTagPath().toStringFull());
                if (mapValue != null) {
                    // 1.0.2 : Support des tags de type Array
                    mapValue.put("Value", Utils.tagValueToString(e.getTag()));
                    mapValue.put("LastChange", e.getTag().getValue().getTimestamp());
                    mapValue.put("Quality", e.getTag().getValue().getQuality().toString());
                    updateTagClient.set(true);
                } else {
                    logger.error("tag changed, not found : {}", e.getTagPath().toStringFull());
                }
                    /*
                }

                } else if (e.getTagProperty().equals(TagProp.LastChange)){
                    logger.info("tagChanged : FullTagPath={}, LastChange={}",e.getTagPath().toStringFull(),e.getTag().getValue().getTimestamp().toString());
                    // TODO : toStringFull voir si retourne le bon path, deja avec provider ?????
                    HashMap mapValue =  mapTags.get(e.getTagPath().toStringFull());
                    if (mapValue!=null) {
                        mapValue.put("LastChange", e.getTag().getValue().getValue().toString());
                        updateTagClient.set(true);
                    } else {
                        logger.error("tag changed, not found : {}", e.getTagPath().toStringFull());
                    }
                } else if (e.getTagProperty().equals(TagProp.Quality)){
                    logger.info("tagChanged : FullTagPath={}, Quality={}",e.getTagPath().toStringFull(),e.getTag().getValue().getQuality().toString());
                    // TODO : toStringFull voir si retourne le bon path, deja avec provider ?????
                    HashMap mapValue =  mapTags.get(e.getTagPath().toStringFull());
                    if (mapValue!=null) {
                        mapValue.put("Quality", e.getTag().getValue().getValue().toString());
                        updateTagClient.set(true);
                    } else {
                        logger.error("tag changed, not found : {}", e.getTagPath().toStringFull());
                    }
                }*/
                // TODO : pour test
                //updateTagClient.set(true);
                //updateTagClientDataset();
            }catch(Exception ex){
                logger.error(ex.getMessage());
            }
        }
        public TagProp getTagProperty()
        {
            // pour indiquer les changement de prop à prendre en compte
            // null => toutes
            //return (TagProp.Value); => pour cibler par exemple la propriété value
            return null;
        }
    }
}