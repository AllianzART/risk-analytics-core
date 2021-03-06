package org.pillarone.riskanalytics.core.simulation.item.parameter

import groovy.transform.TypeChecked
import org.joda.time.DateTime
import org.pillarone.riskanalytics.core.components.*
import org.pillarone.riskanalytics.core.parameter.*
import org.pillarone.riskanalytics.core.parameterization.AbstractMultiDimensionalParameter
import org.pillarone.riskanalytics.core.parameterization.ConstrainedString
import org.pillarone.riskanalytics.core.parameterization.IParameterObject
import org.pillarone.riskanalytics.core.simulation.item.Parameterization
import org.pillarone.riskanalytics.core.simulation.item.ParametrizedItem
import org.pillarone.riskanalytics.core.simulation.item.Resource

@TypeChecked
class ParameterHolderFactory {

    public static ParameterHolder getHolder(String path, int periodIndex, ResourceHolder value) {
        Resource resource = new Resource(value.name, value.resourceClass)
        resource.versionNumber = value.version
        return new ResourceParameterHolder(path, periodIndex, resource)
    }

    public static ParameterHolder getHolder(String path, int periodIndex, Integer value) {
        return new IntegerParameterHolder(path, periodIndex, value)
    }

    public static ParameterHolder getHolder(String path, int periodIndex, Double value) {
        return new DoubleParameterHolder(path, periodIndex, value)
    }

    public static ParameterHolder getHolder(String path, int periodIndex, BigDecimal value) {
        return getHolder(path, periodIndex, value.doubleValue())
    }

    public static ParameterHolder getHolder(String path, int periodIndex, Boolean value) {
        return new BooleanParameterHolder(path, periodIndex, value)
    }

    public static ParameterHolder getHolder(String path, int periodIndex, String value) {
        return new StringParameterHolder(path, periodIndex, value)
    }

    public static ParameterHolder getHolder(String path, int periodIndex, DateTime value) {
        return new DateParameterHolder(path, periodIndex, value)
    }

    public static ParameterHolder getHolder(String path, int periodIndex, ConstrainedString value) {
        return new ConstrainedStringParameterHolder(path, periodIndex, value)
    }

    public static ParameterHolder getHolder(String path, int periodIndex, Enum value) {
        return new EnumParameterHolder(path, periodIndex, value)
    }

    public static ParameterHolder getHolder(String path, int periodIndex, IParameterObject value) {
        return new ParameterObjectParameterHolder(path, periodIndex, value)
    }

    public static ParameterHolder getHolder(String path, int periodIndex, AbstractMultiDimensionalParameter value) {
        return new MultiDimensionalParameterHolder(path, periodIndex, value.clone())
    }

    public static ParameterHolder getHolder(String path, int periodIndex, DataSourceDefinition value) {
        return new DataSourceParameterHolder(path, periodIndex, value)
    }

    public static ParameterHolder getHolder(String path, int periodIndex, def value) {
        throw new IllegalArgumentException("Unknown parameter type ${value?.class?.name}")
    }

    public static ParameterHolder getHolder(Parameter parameter) {
        switch (parameter.persistedClass()) {
            case IntegerParameter:
                return createIntegerHolder(parameter)
            case DoubleParameter:
                return createDoubleHolder(parameter)
            case BooleanParameter:
                return createBooleanHolder(parameter)
            case StringParameter:
                return createStringHolder(parameter)
            case ConstrainedStringParameter:
                return createConstrainedStringHolder(parameter)
            case EnumParameter:
                return createEnumHolder(parameter)
            case ParameterObjectParameter:
                return createParameterObjectHolder(parameter)
            case MultiDimensionalParameter:
                return createMultiDimensionalParameterHolder(parameter)
            case DateParameter:
                return createDateHolder(parameter)
            case ResourceParameter:
                return createResourceHolder(parameter)
            case DataSourceParameter:
                return createDataSourceHolder(parameter)
            default:
                throw new RuntimeException("Unknown paramter type: ${parameter.class}")
        }
    }


    private static ParameterHolder createResourceHolder(Parameter parameter) {
        return new ResourceParameterHolder(parameter)
    }

    private static ParameterHolder createIntegerHolder(Parameter parameter) {
        return new IntegerParameterHolder(parameter)
    }

    private static ParameterHolder createDoubleHolder(Parameter parameter) {
        return new DoubleParameterHolder(parameter)
    }

    private static ParameterHolder createBooleanHolder(Parameter parameter) {
        return new BooleanParameterHolder(parameter)
    }

    private static ParameterHolder createStringHolder(Parameter parameter) {
        return new StringParameterHolder(parameter)
    }

    private static ParameterHolder createConstrainedStringHolder(Parameter parameter) {
        return new ConstrainedStringParameterHolder(parameter)
    }

    private static ParameterHolder createDateHolder(Parameter parameter) {
        return new DateParameterHolder(parameter)
    }

    private static ParameterHolder createEnumHolder(Parameter parameter) {
        return new EnumParameterHolder(parameter)
    }

    private static ParameterHolder createParameterObjectHolder(Parameter parameter) {
        return new ParameterObjectParameterHolder(parameter)
    }

    private static ParameterHolder createMultiDimensionalParameterHolder(Parameter parameter) {
        return new MultiDimensionalParameterHolder(parameter)
    }

    private static ParameterHolder createDataSourceHolder(Parameter parameter) {
        return new DataSourceParameterHolder(parameter)
    }

    /**
     * Removes all parameters whose path starts with oldPath and adds copies of the old parameters
     * to the parameterization with the path replaced with newPath.
     * This can be used to rename all parameters of a component inclusive all of their sub component parameters.
     * Furthermore all parameters referencing it are renamed accordingly.
     * If the new path can be found within the configurations already, the method throws an exception to prevent an invalid state.
     */
    public
    static List<String> renamePathOfParameter(ParametrizedItem parameterization, String oldPath, String newPath, Component renamedComponent) {
        parameterization.notDeletedParameterHolders.each { ParameterHolder it ->
            if (it.path.startsWith("$newPath:")) {
                throw new NonUniqueComponentNameException("A component with the name ${renamedComponent.name} already exists in this dynamic composed component")
            }
        }
        List removedParameters = []
        List clonedParameters = []
        parameterization.notDeletedParameterHolders.each { ParameterHolder parameterHolder ->
            if (parameterHolder.path.startsWith(oldPath + ":")) {
                internalRenamePathOfParameter(parameterHolder, removedParameters, clonedParameters, oldPath, newPath, false)
            }
        }
        removedParameters.each { ParameterHolder parameterHolder ->
            parameterization.removeParameter parameterHolder
        }
        clonedParameters.each { ParameterHolder parameterHolder ->
            parameterization.addParameter parameterHolder
        }
        return renameReferencingParameters(parameterization, oldPath, newPath, renamedComponent)
    }

    private
    static ParameterHolder internalRenamePathOfParameter(ParameterHolder parameterHolder, List<ParameterHolder> removedParameters,
                                                         List<ParameterHolder> clonedParameters, String oldPath, String newPath, boolean isNested) {
        ParameterHolder cloned = (ParameterHolder) parameterHolder.clone()
        cloned.path = cloned.path.replace(oldPath, newPath)
        if (!isNested) {
            removedParameters << parameterHolder
            clonedParameters << cloned
        }
        if (cloned instanceof ParameterObjectParameterHolder) {
            // recursive step down
            cloned.getClassifierParameters().clear()
            for (Map.Entry<String, ParameterHolder> nestedParameterHolder : ((ParameterObjectParameterHolder) parameterHolder).classifierParameters.entrySet()) {
                ParameterHolder clonedNested = internalRenamePathOfParameter(nestedParameterHolder.value, removedParameters, clonedParameters, oldPath, newPath, true)
                cloned.getClassifierParameters().put(nestedParameterHolder.key, clonedNested)
            }
        }
        return cloned
    }

    /**
     * @param parameterization
     * @param oldComponentPath
     * @param newComponentPath
     * @return all referencing components being renamed from oldComponentPath to newComponentPath using the marker
     *          interface of the component @ oldComponentPath
     */
    private
    static List<String> renameReferencingParameters(ParametrizedItem parameterization, String oldComponentPath, String newComponentPath, Component renamedComponent) {
        String oldComponentName = ComponentUtils.getComponentNormalizedName(oldComponentPath)
        String newComponentName = ComponentUtils.getComponentNormalizedName(newComponentPath)
        List<Class> markerInterfaces = getMarkerInterface(renamedComponent)
        List<ParameterHolder> markerParameterHolders = []
        for (Class markerClass in markerInterfaces) {
            markerParameterHolders.addAll(affectedParameterHolders(parameterization, markerClass, oldComponentName))
        }
        List<String> referencingPaths = []
        for (ParameterHolder parameterHolder : markerParameterHolders) {
            if (!parameterHolder.removed) {
                for (Class markerClass in markerInterfaces) {
                    if (parameterHolder instanceof IMarkerValueAccessor) {
                        List<String> paths = (parameterHolder as IMarkerValueAccessor).updateReferenceValues(markerClass, oldComponentName, newComponentName)
                        if (paths.size() > 0) {
                            referencingPaths.addAll paths
                        }
                    }
                }
            }
        }
        return referencingPaths
    }

    /**
     * @param parameterization
     * @param componentPath
     * @return model path of all parameters referencing the component using its marker interface
     */
    public
    static List<String> referencingParametersPaths(Parameterization parameterization, String componentPath, Component component) {
        String componentName = ComponentUtils.getComponentNormalizedName(componentPath)
        List<Class> markerInterfaces = getMarkerInterface(component)
        List<ParameterHolder> markerParameterHolders = []
        for (Class markerClass in markerInterfaces) {
            markerParameterHolders.addAll(affectedParameterHolders(parameterization, markerClass, componentPath))
        }
        List<String> referencingPaths = []
        for (ParameterHolder parameterHolder : markerParameterHolders) {
            if (!parameterHolder.removed) {
                for (Class markerClass in markerInterfaces) {
                    if (parameterHolder instanceof IMarkerValueAccessor) {
                        List<String> paths = (parameterHolder as IMarkerValueAccessor).referencePaths(markerClass, componentName)
                        if (paths.size() > 0) {
                            referencingPaths.addAll paths
                        }
                    }
                }
            }
        }
        return referencingPaths
    }

    private
    static List<ParameterHolder> affectedParameterHolders(ParametrizedItem parameterization, Class markerInterface, String componentPath) {
        List<ParameterHolder> referencedParameterHolders = new ArrayList<ParameterHolder>()
        if (markerInterface) {
            for (ParameterHolder parameterHolder : parameterization.notDeletedParameterHolders) {
                if (parameterHolder instanceof IMarkerValueAccessor) {
                    referencedParameterHolders.add parameterHolder
                }
            }
        }
        return referencedParameterHolders
    }

    /**
     * @param parameterization
     * @param path of component
     * @return class of the marker interface the component @path is implementing or <tt>null</tt> if not found
     */
    private static List<Class> getMarkerInterface(Component component) {
        List<Class> result = []
        for (Class intf : component.class.interfaces) {
            if (IComponentMarker.isAssignableFrom(intf)) {
                result << intf
            }
        }
        return result
    }

    public static void duplicateParameters(ParametrizedItem parameterization, String oldPath, String newPath) {
        List clonedParameters = []
        parameterization.notDeletedParameterHolders.each { ParameterHolder parameterHolder ->
            if (parameterHolder.path.startsWith(oldPath + ":")) {
                ParameterHolder cloned = (ParameterHolder) parameterHolder.clone()
                renamePath(cloned, oldPath, newPath)
                clonedParameters << cloned
            }
        }
        clonedParameters.each { ParameterHolder parameterHolder ->
            parameterization.addParameter parameterHolder
        }
    }

    private static void renamePath(ParameterHolder holder, String oldPath, String newPath) {
        holder.path = holder.path.replace(oldPath + ":", newPath + ":")
        if (ParameterObjectParameterHolder.isInstance(holder)) {
            ((ParameterObjectParameterHolder) holder).classifierParameters.values().each { ParameterHolder subHolder ->
                renamePath(subHolder, oldPath, newPath)
            }
        }
    }
}
