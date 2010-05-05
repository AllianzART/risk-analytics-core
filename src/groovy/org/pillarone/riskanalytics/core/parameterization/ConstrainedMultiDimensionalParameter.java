package org.pillarone.riskanalytics.core.parameterization;

import org.pillarone.riskanalytics.core.components.Component;
import org.pillarone.riskanalytics.core.components.IComponentMarker;
import org.pillarone.riskanalytics.core.model.Model;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

public class ConstrainedMultiDimensionalParameter extends TableMultiDimensionalParameter {

    private IMultiDimensionalConstraints constraints;

    public ConstrainedMultiDimensionalParameter(List cellValues, List titles, IMultiDimensionalConstraints constraints) {
        super(cellValues, titles);
        this.constraints = constraints;
    }


    @Override
    public void setValueAt(Object value, int row, int column) {
        if (constraints.matches(row, column - 1, value)) {
            super.setValueAt(value, row, column);
        } else {
            throw new IllegalArgumentException("Value does not pass constraints");
        }
    }

    public IMultiDimensionalConstraints getConstraints() {
        return constraints;
    }

    public void setSimulationModel(Model simulationModel) {
        this.simulationModel = simulationModel;
    }

    public void validateValues() {
        // column 0 for the index 0,1,2,3,...
        int col = 1;
        for (List list : values) {
            int row = 0;
            for (Object value : list) {
                Object possibleValues = getPossibleValues(row + 1, col);
                if (possibleValues instanceof List) {
                    List<String> validValues = (List<String>) possibleValues;
                    if (!validValues.contains(value)) {
                        if (validValues.size() > 0) {
                            list.set(row, validValues.get(0));
                        }
                    }
                }
                row++;
            }
            col++;
        }
    }

    @Override
    public Object getPossibleValues(int row, int column) {
        if (row == 0 || column == 0) {
            return new Object();
        }
        Class columnClass = constraints.getColumnType(column - 1);
        if (IComponentMarker.class.isAssignableFrom(columnClass)) {
            List<String> names = new ArrayList<String>();
            List components = simulationModel.getMarkedComponents(columnClass);
            for (Object component : components) {
                names.add(normalizeName(((Component) component).getName()));
            }
            return names;
        } else {
            return new Object();
        }
    }

    protected void appendAdditionalConstructorArguments(StringBuffer buffer) {
        super.appendAdditionalConstructorArguments(buffer);
        buffer.append(", ");
        buffer.append("org.pillarone.riskanalytics.core.parameterization.ConstraintsFactory.getConstraints('").append(constraints.getName()).append("')");
    }

    @Override
    public boolean supportsZeroRows() {
        return true;
    }

    @Override
    protected Object createDefaultValue(int row, int column, Object object) {
        Object result = "";
        Class columnClass = constraints.getColumnType(column);
        if (IComponentMarker.class.isAssignableFrom(columnClass)) {
            List list = (List) getPossibleValues(1, column + 1);
            if (list != null && list.size() > 0)
                result = list.get(0);
        } else if (columnClass == BigDecimal.class) {
            result = new BigDecimal(0);
        } else if (columnClass == Double.class) {
            result = 0d;
        } else if (columnClass == Integer.class) {
            result = 0;
        } else {
            try {
                result = columnClass.newInstance();
            } catch (Exception e) {
                throw new RuntimeException(columnClass.getSimpleName() + " not supported as column type", e);
            }
        }
        return result;
    }

    @Override
    public ConstrainedMultiDimensionalParameter clone() throws CloneNotSupportedException {
        final ConstrainedMultiDimensionalParameter clone = (ConstrainedMultiDimensionalParameter) super.clone();
        clone.constraints = constraints;
        return clone;
    }
}