package com.byes.ignition.client.utils;


import com.inductiveautomation.ignition.common.Dataset;
import com.inductiveautomation.ignition.common.sqltags.model.Tag;
import com.inductiveautomation.ignition.common.sqltags.model.types.DataType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class Utils {
    private static final Logger logger = LoggerFactory.getLogger("Utils");
    public static boolean isTagArray(Tag tag) {
        try {
            if (tag.getDataType().equals(DataType.BooleanArray) ||
                tag.getDataType().equals(DataType.DateTimeArray) ||
                tag.getDataType().equals(DataType.Float4Array) ||
                tag.getDataType().equals(DataType.Float8Array) ||
                tag.getDataType().equals(DataType.Int1Array) ||
                tag.getDataType().equals(DataType.Int2Array) ||
                tag.getDataType().equals(DataType.Int4Array) ||
                tag.getDataType().equals(DataType.Int8Array) ||
                tag.getDataType().equals(DataType.StringArray)) {
                return true;
            } else {
                return false;
            }
        } catch (Exception ex) {
            logger.error(ex.getMessage());
            return false;
        }
    }

    public static boolean isTagDataset(Tag tag) {
        try {
            if (tag.getDataType().equals(DataType.DataSet)) {
                return true;
            } else {
                return false;
            }
        } catch (Exception ex) {
            logger.error(ex.getMessage());
            return false;
        }
    }

    public static String tagValueToString(Tag tag) {
        try {
            if (tag==null){
                return "null";
            } else if (tag.getValue().getValue() == null) {
                return "null";
            } else if (isTagArray(tag)){
                Object[] tab = (Object[]) (tag.getValue().getValue());
                String valeurTab = "";
                for (int i=0;i < tab.length;i++){
                    if (!valeurTab.isEmpty()) valeurTab += ",";
                    valeurTab += tab[i].toString();
                }
                valeurTab = "[" + valeurTab + "]";
                return valeurTab;
            } else if (isTagDataset(tag)){
                Dataset data = (Dataset) tag.getValue().getValue();
                // TODO : revoir gestion string plus efficace
                String valeurData = "";
                for (int row=0;row < data.getRowCount();row++){
                    //1.0.4
                    if (valeurData.isEmpty()){
                        valeurData = valeurData + "[";
                    } else {
                        valeurData = valeurData + ",[";
                    }
                    for (int col=0;col < data.getColumnCount();col++) {
                        valeurData += ((data.getValueAt(row, col) == null) ? "null" : data.getValueAt(row, col).toString());
                        if (col < data.getColumnCount()-1){
                            valeurData += ",";
                        }
                    }
                    valeurData = valeurData + "]";
                }
                valeurData = "[" + valeurData + "]";
                return valeurData;
            } else {
                return tag.getValue().getValue().toString();
            }
        } catch (Exception ex) {
            logger.error(ex.getMessage());
            return "Exception !";
        }
    }
}
