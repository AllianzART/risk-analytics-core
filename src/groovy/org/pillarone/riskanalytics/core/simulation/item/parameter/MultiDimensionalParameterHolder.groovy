package org.pillarone.riskanalytics.core.simulation.item.parameter

import groovy.transform.CompileStatic
import org.pillarone.riskanalytics.core.parameter.MultiDimensionalParameter
import org.pillarone.riskanalytics.core.parameter.Parameter
import org.pillarone.riskanalytics.core.parameterization.AbstractMultiDimensionalParameter
import org.pillarone.riskanalytics.core.parameterization.ComboBoxTableMultiDimensionalParameter
import org.pillarone.riskanalytics.core.parameterization.ConstrainedMultiDimensionalParameter
import org.pillarone.riskanalytics.core.parameterization.ComboBoxMatrixMultiDimensionalParameter

class MultiDimensionalParameterHolder extends ParameterHolder implements IMarkerValueAccessor {

    private AbstractMultiDimensionalParameter value;

    public MultiDimensionalParameterHolder(Parameter parameter) {
        super(parameter);
    }

    public MultiDimensionalParameterHolder(String path, int periodIndex, AbstractMultiDimensionalParameter value) {
        super(path, periodIndex);
        this.value = value;
    }

    @Override
    void setParameter(Parameter parameter) {
        this.value = parameter.parameterInstance
    }

    @CompileStatic
    AbstractMultiDimensionalParameter getBusinessObject() {
        return value;
    }

    void applyToDomainObject(Parameter parameter) {
        parameter.parameterInstance = value
    }

    @CompileStatic
    Parameter createEmptyParameter() {
        return new MultiDimensionalParameter(path: path, periodIndex: periodIndex)
    }

    @CompileStatic
    protected void updateValue(Object newValue) {
        value = (AbstractMultiDimensionalParameter) newValue
    }

    @CompileStatic
    public MultiDimensionalParameterHolder clone() {
        MultiDimensionalParameterHolder holder = (MultiDimensionalParameterHolder) super.clone();
        holder.@value = (AbstractMultiDimensionalParameter) value.clone()
        return holder
    }

    @CompileStatic
    List<String> referencePaths(Class markerInterface, String refValue) {
        List<String> paths = new ArrayList()
        if ((value instanceof ConstrainedMultiDimensionalParameter)
                && ((ConstrainedMultiDimensionalParameter) value).referencePaths(markerInterface, refValue)) {
            paths.add(path)
        }
        else if ((value instanceof ComboBoxTableMultiDimensionalParameter) && markerInterface.is((value as ComboBoxTableMultiDimensionalParameter).markerClass)) {
            List list = (List) value.values[0]
            if (list.indexOf(refValue) > -1) {
                paths.add(path)
            }
        } else if ((value instanceof ComboBoxMatrixMultiDimensionalParameter) && markerInterface.is((value as ComboBoxMatrixMultiDimensionalParameter).markerClass)) {
            if (value.columnNames.contains(refValue)) {
                paths.add(path)
            }
        }
        return paths
    }

    @CompileStatic
    List<String> updateReferenceValues(Class markerInterface, String oldValue, String newValue) {
        List<String> referencePaths = referencePaths(markerInterface, oldValue)
        if (referencePaths) {
            boolean modified = false
            if (value instanceof ConstrainedMultiDimensionalParameter) {
                modified = ((ConstrainedMultiDimensionalParameter) value).updateReferenceValues(markerInterface, oldValue, newValue)
            }
            else if (value instanceof ComboBoxTableMultiDimensionalParameter) {
                List list = (List) value.values[0]
                int row = list.indexOf(oldValue)
                row += value.getTitleRowCount()
                if (row > -1) {
                    modified = true
                    value.setValueAt newValue, row, 0
                }
            }
            else if (value instanceof ComboBoxMatrixMultiDimensionalParameter) {
                int row = value.columnNames.indexOf(oldValue)
                if (row > -1) {
                    modified = true
                    value.setValueAt(newValue, row + 1, 0)
                }
            }
            if (modified) {
                setModified(true)
            }
        }
        return referencePaths
    }

    @Override
    @CompileStatic
    void clearCachedValues() {
        if (value != null) {
            value.simulationModel = null
        }
    }


}
