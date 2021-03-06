package org.pillarone.riskanalytics.core.parameter

import groovy.sql.GroovyRowResult
import groovy.sql.Sql
import net.sf.jasperreports.engine.util.ContextClassLoaderObjectInputStream
import org.pillarone.riskanalytics.core.parameterization.*
import org.pillarone.riskanalytics.core.util.DatabaseUtils
import org.springframework.jdbc.datasource.DataSourceUtils

import javax.sql.DataSource

class MultiDimensionalParameter extends Parameter {

    String className
    String constraintName
    String markerClassName
    Set<MultiDimensionalParameterValue> multiDimensionalParameterValues
    Set<MultiDimensionalParameterTitle> multiDimensionalParameterTitles

    Object parameterObject

    DataSource dataSource

    static transients = ['parameterObject', 'dataSource']

    static hasMany = [multiDimensionalParameterValues: MultiDimensionalParameterValue,
            multiDimensionalParameterTitles: MultiDimensionalParameterTitle]

    static constraints = {
        markerClassName(nullable: true)
        constraintName(nullable: true)
    }

    void setParameterInstance(Object value) {
        value = value as AbstractMultiDimensionalParameter
        this.className = value.class.name
        extractValues(value.values)
        extractRowTitles(value.rowNames, value.titleRowCount)
        extractColumnTitles(value.columnNames, value.titleColumnCount)
        parameterObject = null
        markerClassName = value instanceof IComboBoxBasedMultiDimensionalParameter ? value.markerClass.name : null
        constraintName = value instanceof ConstrainedMultiDimensionalParameter ? value.constraints.name : null
        removeObsoleteParameters(value.valueRowCount, value.valueColumnCount)
        removeObsoleteTitles(value.rowCount, value.columnCount)
    }

    private void extractRowTitles(List titles, int offset) {
        boolean isNestedList = titles.any { it instanceof List }
        if (!isNestedList) {
            titles = [titles]
        }

        for (int i = 0; i < titles.size(); i++) {
            List subList = titles[i]
            for (int j = 0; j < subList.size(); j++) {
                modifyOrCreateParameterTitle(j + offset, i, subList[j].toString())
            }
        }
    }

    private void extractColumnTitles(List titles, int offset) {
        boolean isNestedList = titles.any { it instanceof List }
        if (!isNestedList) {
            titles = [titles]
        }

        for (int i = 0; i < titles.size(); i++) {
            List subList = titles[i]
            for (int j = 0; j < subList.size(); j++) {
                modifyOrCreateParameterTitle(i, j + offset, subList[j].toString())
            }
        }
    }

    private void extractValues(List values) {
        List multiDimensionalParameterValuesAsList = new ArrayList()
        if (multiDimensionalParameterValues) {
            multiDimensionalParameterValuesAsList.clear()
            multiDimensionalParameterValuesAsList.addAll(multiDimensionalParameterValues)
        }
        if (values.any { it instanceof List }) {
            for (int i = 0; i < values.size(); i++) {
                for (int j = 0; j < values[i].size(); j++) {
                    modifyOrCreateParameterValue(j, i, values[i][j], multiDimensionalParameterValuesAsList)
                }
            }
        } else {
            for (int i = 0; i < values.size(); i++) {
                modifyOrCreateParameterValue(i, 0, values[i], multiDimensionalParameterValuesAsList)
            }
        }
    }

    private void removeObsoleteParameters(int rowCount, int columnCount) {
        def toRemove = multiDimensionalParameterValues.findAll { it.col >= columnCount || it.row >= rowCount }
        for (MultiDimensionalParameterValue value in toRemove) {
            removeFromMultiDimensionalParameterValues(value)
            value.delete()
        }
    }

    private void removeObsoleteTitles(int rowCount, int columnCount) {
        def toRemove = multiDimensionalParameterTitles.findAll { it.col >= columnCount || it.row >= rowCount }
        for (MultiDimensionalParameterTitle value in toRemove) {
            removeFromMultiDimensionalParameterTitles(value)
            value.delete()
        }
    }

    private void modifyOrCreateParameterValue(int row, int col, Object value, List values) {

        MultiDimensionalParameterValue parameterValue = null
        for (int i = 0; i < values.size() && parameterValue == null; i++) {
            MultiDimensionalParameterValue candidate = values[i] as MultiDimensionalParameterValue
            if (candidate.col == col && candidate.row == row) {
                parameterValue = candidate
            }
        }

        ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream()
        ObjectOutputStream stream = new ObjectOutputStream(byteArrayOutputStream)
        if (value instanceof BigDecimal) {
            value = value.doubleValue()
        }
        stream.writeObject(value)

        byte[] newValue = byteArrayOutputStream.toByteArray()
        if (parameterValue != null) {
            parameterValue.value = newValue
        } else {
            //not using the map constructor syntax in domain objects increases performance
            MultiDimensionalParameterValue multiDimensionalParameterValue = new MultiDimensionalParameterValue()
            multiDimensionalParameterValue.col = col
            multiDimensionalParameterValue.row = row
            multiDimensionalParameterValue.value = newValue
            addToMultiDimensionalParameterValues(multiDimensionalParameterValue)
        }
    }

    private void modifyOrCreateParameterTitle(int row, int col, String value) {
        MultiDimensionalParameterTitle title = multiDimensionalParameterTitles.find { it.col == col && it.row == row }
        if (title != null) {
            title.title = value
        } else {
            addToMultiDimensionalParameterTitles(new MultiDimensionalParameterTitle(col: col, row: row, title: value))
        }
    }

    /**
     * This method should only be called on a persisted, non-dirty object, because the data is read directly from the DB
     * for performance reasons.
     */
    Object getParameterInstance() {
        Class clazz = Thread.currentThread().contextClassLoader.loadClass(className)
        Class markerClass = null
        if (markerClassName != null) {
            markerClass = Thread.currentThread().contextClassLoader.loadClass(markerClassName)
        }
        def mdpInstance = null
        switch (className) {
            case SimpleMultiDimensionalParameter.name:
                mdpInstance = clazz.newInstance([getCellValues()] as Object[])
                break;
            case TableMultiDimensionalParameter.name:
                mdpInstance = clazz.newInstance([getCellValues(), getColumnTitles()] as Object[])
                break;
            case MatrixMultiDimensionalParameter.name:
                mdpInstance = clazz.newInstance([getCellValues(), getRowTitles(), getColumnTitles()] as Object[])
                break;
            case ComboBoxMatrixMultiDimensionalParameter.name:
                mdpInstance = clazz.newInstance([getCellValues(), getColumnTitles(), markerClass] as Object[])
                break;
            case ComboBoxTableMultiDimensionalParameter.name:
                mdpInstance = clazz.newInstance([getCellValues(), getColumnTitles(), markerClass] as Object[])
                break;
            case ConstrainedMultiDimensionalParameter.name:
                mdpInstance = clazz.newInstance([getCellValues(), getColumnTitles(), ConstraintsFactory.getConstraints(constraintName)] as Object[])
                break;
            case PeriodMatrixMultiDimensionalParameter.name:
                mdpInstance = clazz.newInstance([getCellValues(), getRowTitlesForPeriodMatrix(), markerClass] as Object[])
                break;
        }
        parameterObject = mdpInstance
        return parameterObject
    }

    private List getColumnTitles() {
        return multiDimensionalParameterTitles.findAll { it.row == 0 }.sort { it.col }.collect { it.title }
    }

    private List getRowTitles() {
        return multiDimensionalParameterTitles.findAll { it.col == 0 }.sort { it.row }.collect { it.title }
    }

    private List getRowTitlesForPeriodMatrix() {
        List result = []
        multiDimensionalParameterTitles*.col.unique().sort().each { int col ->
            result << multiDimensionalParameterTitles.findAll { it.col == col }.sort { it.row }.collect { it.title }
        }

        return result
    }

    private List getCellValues() {
        List result = []
        Sql sql = new Sql(DataSourceUtils.getConnection(dataSource))
        int i = 0
        String query = DatabaseUtils.isOracleDatabase() ?
                "SELECT value FROM mdp_value v where v.mdp_id = ? and v.col = ? order by v.row_number" :
                "SELECT value FROM multi_dimensional_parameter_value v where v.multi_dimensional_parameter_id = ? and v.col = ? order by v.row"

        List column = sql.rows(query, [this.id, i])
        while (column.size() > 0) {
            result << column.collect { GroovyRowResult res ->
                ByteArrayInputStream str = new ByteArrayInputStream(res.getAt(0))
                ObjectInputStream str2 = new ContextClassLoaderObjectInputStream(str)
                str2.readObject()
            }
            i++
            column = sql.rows(query, [this.id, i])
        }
        if (result.size() == 0) {
            return []
        }
        return result.size() > 1 ? result : result.get(0)
    }

    Class persistedClass() {
        MultiDimensionalParameter
    }
}
