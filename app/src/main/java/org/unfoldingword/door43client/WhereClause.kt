package org.unfoldingword.door43client;

import android.content.ContentValues;
import android.text.TextUtils;

import java.util.ArrayList;
import java.util.List;

/**
 * This is a utility class for preparing a where clause
 */
class WhereClause {
    public final String statement;
    public final String[] arguments;

    private WhereClause(String statement, String[] values) {
        this.statement = statement;
        this.arguments = values;
    }

    /**
     * Performs a bunch of magical operations to convert a set of values and the specified unique columns
     * into a valid where clause with supporting values.
     *
     *
     *
     * @param values
     * @param uniqueColumns
     * @return
     */
    public static WhereClause prepare(ContentValues values, String[] uniqueColumns) {
        List<String> stringColumns = new ArrayList<>();
        List<String> numberColumns = new ArrayList<>();

        // split columns into sets by type
        for(String key:uniqueColumns) {
            if(values.get(key) instanceof String) {
                stringColumns.add(key);
            } else {
                numberColumns.add(key);
            }
        }

        // build the statement
        String whereStmt = "";
        if(stringColumns.size() > 0) {
            whereStmt = TextUtils.join("=? and ", stringColumns) + "=?";
        }
        if(numberColumns.size() > 0) {
            if (!whereStmt.isEmpty()) whereStmt += " and ";
            List<String> expressions = new ArrayList<>();
            for(String key:numberColumns) {
                expressions.add(key + "=" + values.get(key));
            }
            whereStmt += TextUtils.join(" and ", expressions);
        }

        // build the values
        String[] uniqueValues = new String[stringColumns.size()];
        for(int i = 0; i < stringColumns.size(); i ++) {
            uniqueValues[i] = String.valueOf(values.get(stringColumns.get(i)));
        }

        return new WhereClause(whereStmt, uniqueValues);
    }
}