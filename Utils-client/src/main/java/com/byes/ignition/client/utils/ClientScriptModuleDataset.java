package com.byes.ignition.client.utils;

import com.inductiveautomation.ignition.client.model.ClientContext;
import com.inductiveautomation.ignition.common.BasicDataset;
import com.inductiveautomation.ignition.common.BundleUtil;
import com.inductiveautomation.ignition.common.Dataset;
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
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

public class ClientScriptModuleDataset{
    static {
        BundleUtil.get().addBundle(
                ClientScriptModuleDataset.class.getSimpleName(),
                ClientScriptModuleDataset.class.getClassLoader(),
                ClientScriptModuleDataset.class.getName().replace('.', '/'));
    }

    private final String TAGPATH_CLIENT_DATASET = "[Client]ds_subscription";
    private final Integer UPDATE_FREQUENCY_MS = 250;
    private final String EXECUTION_ENGINE_NAME = "CyclicUpdateTagClientDatasetFromDataset";

    private final Logger logger = LoggerFactory.getLogger(getClass());
    private ClientContext clientContext;

    // flag de demande update cyclique du dataset client
    private AtomicBoolean updateTagClient = new AtomicBoolean(false);
    private BasicExecutionEngine executionEngine = null;
    private ExecutionCyclic executionCyclic = null;

    private boolean flagRegistered = false;

    private int datasetColIndexTagFullPath = -1;
    private int datasetColIndexValue = -1;
    private int datasetColIndexLastChange = -1;
    private int datasetColIndexQuality = -1;

    private boolean colIndexTagFullPathOk = false;
    private boolean colIndexValueOk = false;
    private boolean colIndexLastChangeOk = false;
    private boolean colIndexQualityOk = false;

    private BasicDataset dataset = null;
    private List<TagPath> listSubscribedTagPath = new ArrayList<TagPath>();
    private List<TagChangeListener> listSubscribedTagChangeListener = new ArrayList<TagChangeListener>();

    public ClientScriptModuleDataset(ClientContext _clientContext) {
        try {
            logger.debug("Constructor ClientScriptModuleDataset");
            this.clientContext = _clientContext;
        } catch (Exception e) {
            logger.error("error : ",e);
        }
    }

    @ScriptFunction(docBundlePrefix = "ClientScriptModuleDataset")
    public void subscribe(@ScriptArg("datasetInput") Dataset datasetInput) {
        try {
            // désabonnement du dataset précédant
            unsubscribeAll();

            if (datasetInput!=null){
                List<String> columnNames = datasetInput.getColumnNames();
                // recherche des index des colonnes qui seront maj
                // et vérification des types avec le dataset fournit
                int index = 0;
                for (String col : columnNames){
                    if (col.equalsIgnoreCase("TagFullPath")){
                        datasetColIndexTagFullPath = index;
                        if (datasetInput.getColumnType(datasetColIndexTagFullPath) == String.class){
                            colIndexTagFullPathOk = true;
                        }
                    } else if (col.equalsIgnoreCase("Value")){
                        datasetColIndexValue = index;
                        colIndexValueOk = true;
                    } else if (col.equalsIgnoreCase("LastChange")){
                        datasetColIndexLastChange = index;
                        colIndexLastChangeOk = true;
                    } else if (col.equalsIgnoreCase("Quality")) {
                        datasetColIndexQuality = index;
                        colIndexQualityOk = true;
                    }
                    index ++;
                }

                if (!colIndexTagFullPathOk || !colIndexValueOk){
                    logger.error("dataset must contains the columns : TagFullPath and Value (optional : LastChange,Quality)");
                }else{
                    // reconfiguration des types des colonnes du dataset qui sont ecrites
                    // /!\ on fait une copie, sinon pas modifiable !
                    List<Class<?>> colTypes = new ArrayList<Class<?>>(datasetInput.getColumnTypes());
                    //logger.debug("colTypes = {}",colTypes.toString());
                    //logger.debug("columnNames = {}",columnNames.toString());
                    if (colIndexValueOk){
                        logger.debug("datasetColIndexValue={}",datasetColIndexValue);
                        colTypes.set(datasetColIndexValue,String.class);
                    }
                    if (colIndexLastChangeOk){
                        colTypes.set(datasetColIndexLastChange,java.util.Date.class);
                    }
                    if (colIndexQualityOk){
                        colTypes.set(datasetColIndexQuality,String.class);
                    }
                    // copie du contenu référence qui pointe sur celui crée dans le script python
                    // mais détruit à la fin du script de souscription
                    // et modification des types de colonnes si besoin
                    dataset = new BasicDataset(columnNames,colTypes,datasetInput);
                    // souscription des tag
                    subscribeDataset();
                }
            } else {
                logger.error("dataset is null");
            }
        } catch (Exception e) {
            logger.error("error : ",e);
        }
    }

    @ScriptFunction(docBundlePrefix = "ClientScriptModuleDataset")
    @KeywordArgs(names = {"dataset","indexTagFullPath","indexValue","indexLastChange","indexQuality"},
                types = {Dataset.class,Integer.class,Integer.class,Integer.class,Integer.class})
    public void subscribeWK(PyObject[] pyArgs, String[] keywords)
    {
        try {
            PyArgumentMap args = PyArgumentMap.interpretPyArgs(pyArgs, keywords, ClientScriptModuleDataset.class, "subscribeWK");

            Dataset datasetInput = (Dataset) args.getArg("dataset",null);
            datasetColIndexTagFullPath = args.getIntArg("indexTagFullPath",-1);
            datasetColIndexValue = args.getIntArg("indexValue",-1);
            datasetColIndexLastChange = args.getIntArg("indexLastChange",-1);
            datasetColIndexQuality = args.getIntArg("indexQuality",-1);

            colIndexTagFullPathOk = false;
            colIndexValueOk = false;
            colIndexLastChangeOk = false;
            colIndexQualityOk = false;

            if (datasetInput!=null){
                // désabonnement du dataset précédant
                unsubscribeAll();
                // Vérification des index des colonnes qui seront maj compatible avec le dataset fourni
                if ((datasetColIndexTagFullPath >= 0) && (datasetColIndexTagFullPath < datasetInput.getRowCount())){
                    if (datasetInput.getColumnType(datasetColIndexTagFullPath) == String.class){
                        colIndexTagFullPathOk = true;
                    }
                }
                if ((datasetColIndexValue >= 0) && (datasetColIndexValue < datasetInput.getRowCount())){
                    colIndexValueOk = true;
                }
                if ((datasetColIndexLastChange >= 0) && (datasetColIndexLastChange < datasetInput.getRowCount())){
                    colIndexLastChangeOk = true;
                }
                if ((datasetColIndexQuality >= 0) && (datasetColIndexQuality < datasetInput.getRowCount())){
                    colIndexQualityOk = true;
                }
                if (!colIndexTagFullPathOk || !colIndexValueOk){
                    logger.error("dataset must contains the columns : TagFullPath and Value (optional : LastChange,Quality)");
                }else{
                    // reconfiguration des types des colonnes du dataset qui sont ecrites
                    // /!\ on fait une copie, sinon pas modifiable !
                    List<Class<?>> colTypes = new ArrayList<Class<?>>(datasetInput.getColumnTypes());
                    if (colIndexValueOk){
                        colTypes.set(datasetColIndexValue,String.class);
                    }
                    if (colIndexLastChangeOk){
                        colTypes.set(datasetColIndexLastChange,java.util.Date.class);
                    }
                    if (colIndexQualityOk){
                        colTypes.set(datasetColIndexQuality,String.class);
                    }
                    // copie du contenu référence qui pointe sur celui crée dans le script python
                    // mais détruit à la fin du script de souscription
                    // et modification des types de colonnes si besoin
                    dataset = new BasicDataset(datasetInput.getColumnNames(),colTypes,datasetInput);
                    // souscription des tag
                    subscribeDataset();
                }
            } else {
                logger.error("dataset is null");
            }
        } catch (Exception e) {
            logger.error("error : ",e);
        }
    }

    @NoHint
    public void subscribeDataset() {
        // souscription des tag
        List<String> listTagPath = new ArrayList<String>();
        for (int row = 0; row < dataset.getRowCount(); row++) {
            listTagPath.add(dataset.getValueAt(row, datasetColIndexTagFullPath).toString());
        }
        try {
            if (checkTagClientExist() == false) {
                logger.error("You Must create tag client for subscription update : {}", TAGPATH_CLIENT_DATASET);
            }
            // Abonnement sur la propriété Value => cf MyTagChangeListener
            int rowIndex = 0;
            for (String fullTagPath : listTagPath) {
                // fonction sans le null nok
                //TagPath tagPath = TagPathParser.parse("default",null,fullTagPath);
                TagPath tagPath;
                tagPath = TagPathParser.parseSafe("default", fullTagPath);
                // Pas de ctrl tagPath=null, pour ne pas décaler les indice entre les liste et les row du dataset
                //if (tagPath!=null){
                // Pas utile de contrôler si un tag apparaît plusieurs fois dans le dataset
                // => plusieurs abonnements sur le même tag si besoin
                listSubscribedTagPath.add(tagPath);
                //logger.info("add " + tagPath.toStringFull());
                // TODO : voir si utiliser le même listener ou un par tag ??? ou gestion synchronised ???
                // Numero de la ligne dans le dataset
                MyTagChangeListener listener = new MyTagChangeListener(rowIndex);
                listSubscribedTagChangeListener.add(listener);
                //}else{
                //    logger.error("parseSafe for : {} return null",fullTagPath);
                //}
                rowIndex++;
            }
            if (!listSubscribedTagPath.isEmpty()) {

                // lecture des données des Tag ajoutés pour les mettre dans le tag client dataset
                // TODO : pas utlise, première valeur mis à jour lors de l'abonnement ????
                //List<Tag> listTag = this.clientContext.getTagManager().getTags(listAdd);
                // Abonnement des tag ajoutés
                this.clientContext.getTagManager().subscribe(listSubscribedTagPath, listSubscribedTagChangeListener);
                logger.debug("Add subscriptions for VTQ of {} tags", listSubscribedTagPath.size());

                // création la première fois
                if (executionEngine == null) {
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

    @ScriptFunction(docBundlePrefix = "ClientScriptModuleDataset")
    public void unsubscribeAll() {
        try {
            if ((listSubscribedTagPath != null) && (listSubscribedTagChangeListener != null)){
                if (!listSubscribedTagPath.isEmpty() && !listSubscribedTagChangeListener.isEmpty()){
                    this.clientContext.getTagManager().unsubscribe(listSubscribedTagPath,listSubscribedTagChangeListener);
                    logger.debug("Delete subscriptions for VTQ of {} tags",listSubscribedTagPath.size());
                    listSubscribedTagPath.clear();
                    listSubscribedTagChangeListener.clear();
                }
            }
            dataset = null;
            updateTagClientDataset();
        } catch (Exception e) {
            logger.error("error : ",e);
        }
    }

    // force la relecture de l'ensemble des tags
    // et la maj du dataset client
    @ScriptFunction(docBundlePrefix = "ClientScriptModuleDataset")
    public void forceUpdateTagClientDataset(){
        try {
            if (dataset != null){
                try{
                    // /!\ bug Ignition => fonction ne marche pas dans le contexte client
                    // com.inductiveautomation.ignition.client.gateway_interface.GatewayException: Unable to locate function 'SQLTags.read'
                    // cf post https://inductiveautomation.com/forum/viewtopic.php?f=74&t=16733
                    //List<QualifiedValue> listQv = this.clientContext.getTagManager().read(listTagRead);
                    List<Tag> listTag = this.clientContext.getTagManager().getTags(listSubscribedTagPath);
                    int rowIndex = 0;
                    // la liste des résultats de la lecture est ordonnée selon la liste des tag à lire
                    for (Tag tag : listTag){
                        if (rowIndex > dataset.getRowCount()){
                            logger.error("rowIndex={} > dataset.getRowCount={}",rowIndex,dataset.getRowCount());
                        } else {
                            dataset.setValueAt(rowIndex,datasetColIndexValue, (tag.getValue().getValue() == null) ? "null" : tag.getValue().getValue().toString());
                            // 1.0.2 : Support des tags de type Array - ajout ctrl colxxxOk
                            if (colIndexValueOk) dataset.setValueAt(rowIndex,datasetColIndexValue,Utils.tagValueToString(tag));
                            if (colIndexLastChangeOk) dataset.setValueAt(rowIndex,datasetColIndexLastChange, tag.getValue().getTimestamp());
                            if (colIndexQualityOk) dataset.setValueAt(rowIndex,datasetColIndexQuality, tag.getValue().getQuality().toString());
                        }
                        rowIndex ++;
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
            if (updateTagClient.compareAndSet(true,false)){
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
                executionEngine.unRegister("Byes-Utils-Module", EXECUTION_ENGINE_NAME);
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
            this.clientContext.getTagManager().write(TagPathParser.parse("client", TAGPATH_CLIENT_DATASET), dataset);
            if (dataset==null){
                stopCyclicUpdateTagClientDataset();
            }
        }catch(Exception e){
            logger.error("error : ",e);
        }
    }

    private class MyTagChangeListener implements TagChangeListener {
        // Nota : pas de filtrage du premier évènement qui permet de mettre à jour la première valeur
        private int rowIndex = -1;
        public MyTagChangeListener(int _rowIndex){
            super();
            this.rowIndex = _rowIndex;
        }

        @Override
        public void tagChanged(TagChangeEvent e)
        {
            try{
                logger.debug("tagChanged : FullTagPath={}, Value={}, LastChange={}", e.getTagPath().toStringFull()
                                                                    , (e.getTag().getValue().getValue() == null) ? "null" : e.getTag().getValue().getValue().toString()
                                                                    , e.getTag().getValue().getTimestamp().toString());
                if (dataset == null) {
                    logger.error("dataset is null");
                } else if (rowIndex > dataset.getRowCount()){
                    logger.error("rowIndex={} > dataset.getRowCount={}",rowIndex,dataset.getRowCount());
                } else {
                    // 1.0.2 : Support des tags de type Array
                    if (colIndexValueOk) dataset.setValueAt(rowIndex,datasetColIndexValue,Utils.tagValueToString(e.getTag()));
                    if (colIndexLastChangeOk) dataset.setValueAt(rowIndex,datasetColIndexLastChange, e.getTag().getValue().getTimestamp());
                    if (colIndexQualityOk) dataset.setValueAt(rowIndex,datasetColIndexQuality, e.getTag().getValue().getQuality().toString());
                    updateTagClient.set(true);
                }
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