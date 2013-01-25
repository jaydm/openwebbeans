/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements. See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership. The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.apache.webbeans.component.creation;

import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.lang.reflect.Type;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

import javax.enterprise.context.Dependent;
import javax.enterprise.context.NormalScope;
import javax.enterprise.inject.Any;
import javax.enterprise.inject.Specializes;
import javax.enterprise.inject.spi.Annotated;
import javax.enterprise.inject.spi.AnnotatedField;
import javax.enterprise.inject.spi.AnnotatedMember;
import javax.enterprise.inject.spi.AnnotatedMethod;
import javax.enterprise.inject.spi.AnnotatedParameter;
import javax.enterprise.inject.spi.AnnotatedType;
import javax.enterprise.util.Nonbinding;
import javax.inject.Named;
import javax.inject.Scope;

import org.apache.webbeans.annotation.AnnotationManager;
import org.apache.webbeans.annotation.AnyLiteral;
import org.apache.webbeans.annotation.DefaultLiteral;
import org.apache.webbeans.annotation.NamedLiteral;
import org.apache.webbeans.component.BeanAttributesImpl;
import org.apache.webbeans.config.OWBLogConst;
import org.apache.webbeans.config.WebBeansContext;
import org.apache.webbeans.container.ExternalScope;
import org.apache.webbeans.exception.WebBeansConfigurationException;
import org.apache.webbeans.exception.inject.DefinitionException;
import org.apache.webbeans.logger.WebBeansLoggerFacade;
import org.apache.webbeans.util.AnnotationUtil;
import org.apache.webbeans.util.Asserts;
import org.apache.webbeans.util.ClassUtil;
import org.apache.webbeans.util.WebBeansUtil;

/**
 * Abstract implementation.
 * 
 * @version $Rev$ $Date$
 *
 * @param <T> bean class info
 */
public abstract class BeanAttributesBuilder<T, A extends Annotated>
{
    A annotated;
    
    WebBeansContext webBeansContext;
        
    Set<Type> types = new HashSet<Type>();

    Set<Annotation> qualifiers = new HashSet<Annotation>();
    
    Class<? extends Annotation> scope;
    
    String name;
    
    boolean nullable;
    
    Set<Class<? extends Annotation>> stereotypes = new HashSet<Class<? extends Annotation>>();
    
    boolean alternative;
    
    public static BeanAttributesBuilderFactory forContext(WebBeansContext webBeansContext)
    {
        return new BeanAttributesBuilderFactory(webBeansContext);
    }

    /**
     * Creates a bean instance.
     * 
     * @param annotated
     */
    protected BeanAttributesBuilder(WebBeansContext webBeansContext, A annotated)
    {
        this.annotated = annotated;
        this.webBeansContext = webBeansContext;
    }

    public BeanAttributesImpl<T> build()
    {
        defineTypes();
        defineStereotypes();
        defineScope();
        defineName();
        defineQualifiers();
        defineNullable();
        defineAlternative();
        return new BeanAttributesImpl<T>(types, qualifiers, scope, name, nullable, stereotypes, alternative);
    }

    protected A getAnnotated()
    {
        return annotated;
    }

    /**
     * {@inheritDoc}
     */
    protected void defineTypes()
    {
        Class<?> baseType = ClassUtil.getClass(annotated.getBaseType());
        if (baseType.isArray())
        {
            // 3.3.1
            types.add(Object.class);
            types.add(baseType);
        }
        else
        {
            Set<Type> types = annotated.getTypeClosure();
            this.types.addAll(types);
            Set<String> ignored = webBeansContext.getOpenWebBeansConfiguration().getIgnoredInterfaces();
            for (Iterator<Type> i = this.types.iterator(); i.hasNext();)
            {
                Type t = i.next();
                if (t instanceof Class && ignored.contains(((Class<?>)t).getName()))
                {
                    i.remove();
                }
            }
        }
    }

    /**
     * {@inheritDoc}
     */
    protected void defineQualifiers()
    {
        HashSet<Class<? extends Annotation>> qualifiedTypes = new HashSet<Class<? extends Annotation>>();
        if (annotated.isAnnotationPresent(Specializes.class))
        {
            defineQualifiers(getSuperAnnotated(), qualifiedTypes);
        }
        defineQualifiers(annotated, qualifiedTypes);
    }

    private void defineQualifiers(Annotated annotated, Set<Class<? extends Annotation>> qualifiedTypes)
    {
        Annotation[] annotations = AnnotationUtil.asArray(annotated.getAnnotations());
        final AnnotationManager annotationManager = webBeansContext.getAnnotationManager();

        for (Annotation annotation : annotations)
        {
            Class<? extends Annotation> type = annotation.annotationType();

            if (annotationManager.isQualifierAnnotation(type))
            {
                Method[] methods = webBeansContext.getSecurityService().doPrivilegedGetDeclaredMethods(type);

                for (Method method : methods)
                {
                    Class<?> clazz = method.getReturnType();
                    if (clazz.isArray() || clazz.isAnnotation())
                    {
                        if (!AnnotationUtil.hasAnnotation(method.getDeclaredAnnotations(), Nonbinding.class))
                        {
                            throw new WebBeansConfigurationException("WebBeans definition class : " + method.getDeclaringClass().getName() + " @Qualifier : "
                                                                     + annotation.annotationType().getName()
                                                                     + " must have @NonBinding valued members for its array-valued and annotation valued members");
                        }
                    }
                }

                if (qualifiedTypes.contains(annotation.annotationType()))
                {
                    continue;
                }
                else
                {
                    qualifiedTypes.add(annotation.annotationType());
                }
                if (annotation.annotationType().equals(Named.class) && name != null)
                {
                    qualifiers.add(new NamedLiteral(name));
                }
                else
                {
                    qualifiers.add(annotation);
                }
            }
        }
        
        // No-binding annotation
        if (qualifiers.size() == 0 )
        {
            qualifiers.add(DefaultLiteral.INSTANCE);
        }
        else if(qualifiers.size() == 1)
        {
            Annotation annot = qualifiers.iterator().next();
            if(annot.annotationType().equals(Named.class))
            {
                qualifiers.add(DefaultLiteral.INSTANCE);
            }
        }
        
        //Add @Any support
        if(!hasAnyQualifier())
        {
            qualifiers.add(new AnyLiteral());
        }
        
    }

    /**
     * Returns true if any binding exist
     * 
     * @return true if any binding exist
     */
    private boolean hasAnyQualifier()
    {
        return AnnotationUtil.getAnnotation(qualifiers, Any.class) != null;
    }


    protected abstract void defineScope();

    protected void defineScope(String errorMessage)
    {
        defineScope(null, errorMessage);
    }

    protected void defineScope(Class<?> declaringClass, String errorMessage)
    {
        Annotation[] annotations = AnnotationUtil.asArray(annotated.getAnnotations());
        boolean found = false;

        List<ExternalScope> additionalScopes = webBeansContext.getBeanManagerImpl().getAdditionalScopes();
        
        for (Annotation annotation : annotations)
        {   
            if (declaringClass != null && AnnotationUtil.getDeclaringClass(annotation, declaringClass) != null && !AnnotationUtil.isDeclaringClass(declaringClass, annotation))
            {
                continue;
            }
            Class<? extends Annotation> annotationType = annotation.annotationType();
            
            /*Normal scope*/
            Annotation var = annotationType.getAnnotation(NormalScope.class);
            /*Pseudo scope*/
            Annotation pseudo = annotationType.getAnnotation(Scope.class);
        
            if (var == null && pseudo == null)
            {
                // check for additional scopes registered via a CDI Extension
                for (ExternalScope additionalScope : additionalScopes)
                {
                    if (annotationType.equals(additionalScope.getScope()))
                    {
                        // create a proxy which implements the given annotation
                        Annotation scopeAnnotation = additionalScope.getScopeAnnotation();
    
                        if (additionalScope.isNormal())
                        {
                            var = scopeAnnotation;
                        }
                        else
                        {
                            pseudo = scopeAnnotation;
                        }
                    }
                }
            }
            
            if (var != null)
            {
                if(pseudo != null)
                {
                    throw new WebBeansConfigurationException("Not to define both @Scope and @NormalScope on bean : " + ClassUtil.getClass(annotated.getBaseType()).getName());
                }
                
                if (found)
                {
                    throw new WebBeansConfigurationException(errorMessage);
                }

                found = true;
                scope = annotation.annotationType();
            }
            else
            {
                if(pseudo != null)
                {
                    if (found)
                    {
                        throw new WebBeansConfigurationException(errorMessage);
                    }

                    found = true;
                    scope = annotation.annotationType();
                }
            }
        }

        if (!found && declaringClass != null && !hasDeclaredNonInheritedScope(declaringClass))
        {
            defineScope(declaringClass.getSuperclass(), errorMessage);
        }
        else if (!found)
        {
            defineDefaultScope(errorMessage);
        }
    }

    private void defineDefaultScope(String exceptionMessage)
    {
        if (scope == null)
        {
            Set<Class<? extends Annotation>> stereos = stereotypes;
            if (stereos.size() == 0)
            {
                scope = Dependent.class;
            }
            else
            {
                Annotation defined = null;
                Set<Class<? extends Annotation>> anns = stereotypes;
                for (Class<? extends Annotation> stero : anns)
                {
                    boolean containsNormal = AnnotationUtil.hasMetaAnnotation(stero.getDeclaredAnnotations(), NormalScope.class);
                    
                    if (AnnotationUtil.hasMetaAnnotation(stero.getDeclaredAnnotations(), NormalScope.class) ||
                            AnnotationUtil.hasMetaAnnotation(stero.getDeclaredAnnotations(), Scope.class))
                    {                        
                        Annotation next;
                        
                        if(containsNormal)
                        {
                            next = AnnotationUtil.getMetaAnnotations(stero.getDeclaredAnnotations(), NormalScope.class)[0];
                        }
                        else
                        {
                            next = AnnotationUtil.getMetaAnnotations(stero.getDeclaredAnnotations(), Scope.class)[0];
                        }

                        if (defined == null)
                        {
                            defined = next;
                        }
                        else
                        {
                            if (!defined.equals(next))
                            {
                                throw new WebBeansConfigurationException(exceptionMessage);
                            }
                        }
                    }
                }

                if (defined != null)
                {
                    scope = defined.annotationType();
                }
                else
                {
                    scope = Dependent.class;
                }
            }
        }
    }

    private boolean hasDeclaredNonInheritedScope(Class<?> type)
    {
        return webBeansContext.getAnnotationManager().getDeclaredScopeAnnotation(type) != null;
    }

    protected abstract void defineName();

    protected void defineName(Annotated annotated, String name)
    {
        Annotation[] anns = AnnotationUtil.asArray(annotated.getAnnotations());
        Named nameAnnot = null;
        boolean isDefault = false;
        for (Annotation ann : anns)
        {
            if (ann.annotationType().equals(Named.class))
            {
                nameAnnot = (Named) ann;
                break;
            }
        }

        if (nameAnnot == null) // no @Named
        {
            // Check for stereottype
            if (webBeansContext.getAnnotationManager().hasNamedOnStereoTypes(stereotypes))
            {
                isDefault = true;
            }

        }
        else
        // yes @Named
        {
            if (nameAnnot.value().equals(""))
            {
                isDefault = true;
            }
            else
            {
                this.name = nameAnnot.value();
            }

        }

        if (isDefault)
        {
            this.name = name;
        }
    }
    
    protected abstract Annotated getSuperAnnotated();

    protected abstract void defineNullable();
    
    protected void defineNullable(boolean nullable)
    {
        this.nullable = nullable;
    }

    /**
     * {@inheritDoc}
     */
    protected void defineStereotypes()
    {
        Annotation[] anns = AnnotationUtil.asArray(annotated.getAnnotations());
        final AnnotationManager annotationManager = webBeansContext.getAnnotationManager();
        if (annotationManager.hasStereoTypeMetaAnnotation(anns))
        {
            Annotation[] steroAnns =
                annotationManager.getStereotypeMetaAnnotations(anns);

            for (Annotation stereo : steroAnns)
            {
                stereotypes.add(stereo.annotationType());
            }
        }
    }
    
    protected void defineAlternative()
    {
        alternative = false;
    }
    
    public static class BeanAttributesBuilderFactory
    {
        private WebBeansContext webBeansContext;

        private BeanAttributesBuilderFactory(WebBeansContext webBeansContext)
        {
            Asserts.assertNotNull(webBeansContext, "webBeansContext may not be null");
            this.webBeansContext = webBeansContext;
        }
        
        public <T> BeanAttributesBuilder<T, AnnotatedType<T>> newBeanAttibutes(AnnotatedType<T> annotatedType)
        {
            return new AnnotatedTypeBeanAttributesBuilder<T>(webBeansContext, annotatedType);
        }
        
        public <T> BeanAttributesBuilder<T, AnnotatedField<T>> newBeanAttibutes(AnnotatedField<T> annotatedField)
        {
            return new AnnotatedFieldBeanAttributesBuilder<T>(webBeansContext, annotatedField);
        }
        
        public <T> BeanAttributesBuilder<T, AnnotatedMethod<T>> newBeanAttibutes(AnnotatedMethod<T> annotatedMethod)
        {
            return new AnnotatedMethodBeanAttributesBuilder<T>(webBeansContext, annotatedMethod);
        }
    }

    private static class AnnotatedTypeBeanAttributesBuilder<C> extends BeanAttributesBuilder<C, AnnotatedType<C>>
    {

        public AnnotatedTypeBeanAttributesBuilder(WebBeansContext webBeansContext, AnnotatedType<C> annotated)
        {
            super(webBeansContext, annotated);
        }

        @Override
        protected void defineScope()
        {
            defineScope(getAnnotated().getJavaClass(), WebBeansLoggerFacade.getTokenString(OWBLogConst.TEXT_MB_IMPL) + getAnnotated().getJavaClass().getName() +
                    WebBeansLoggerFacade.getTokenString(OWBLogConst.TEXT_SAME_SCOPE));
        }

        @Override
        protected void defineName()
        {
            if (getAnnotated().isAnnotationPresent(Specializes.class))
            {
                AnnotatedType<? super C> superAnnotated = getSuperAnnotated();
                defineName(superAnnotated, WebBeansUtil.getManagedBeanDefaultName(superAnnotated.getJavaClass().getSimpleName()));
            }
            if (name == null)
            {
                defineName(getAnnotated(), WebBeansUtil.getManagedBeanDefaultName(getAnnotated().getJavaClass().getSimpleName()));
            }
            else
            {
                // TODO XXX We have to check stereotypes here, too
                if (getAnnotated().getJavaClass().isAnnotationPresent(Named.class))
                {
                    throw new DefinitionException("@Specialized Class : " + getAnnotated().getJavaClass().getName()
                            + " may not explicitly declare a bean name");
                }
            }
        }
        
        @Override
        protected void defineNullable()
        {
            defineNullable(false);
        }

        @Override
        protected AnnotatedType<? super C> getSuperAnnotated()
        {
            Class<? super C> superclass = getAnnotated().getJavaClass().getSuperclass();
            if (superclass == null)
            {
                return null;
            }
            return webBeansContext.getAnnotatedElementFactory().newAnnotatedType(superclass);
        }
    }
    
    private static class AnnotatedFieldBeanAttributesBuilder<M> extends AnnotatedMemberBeanAttributesBuilder<M, AnnotatedField<M>>
    {

        protected AnnotatedFieldBeanAttributesBuilder(WebBeansContext webBeansContext, AnnotatedField<M> annotated)
        {
            super(webBeansContext, annotated);
        }

        @Override
        protected void defineScope()
        {
            defineScope("Annotated producer field: " + getAnnotated().getJavaMember() +  "must declare default @Scope annotation");
        }

        @Override
        protected void defineName()
        {
            defineName(getAnnotated(), WebBeansUtil.getProducerDefaultName(getAnnotated().getJavaMember().getName()));
        }
        
        @Override
        protected void defineNullable()
        {
            defineNullable(!getAnnotated().getJavaMember().getType().isPrimitive());
        }

        @Override
        protected AnnotatedField<? super M> getSuperAnnotated()
        {
            AnnotatedField<M> thisField = getAnnotated();
            for (AnnotatedField<? super M> superField: getSuperType().getFields())
            {
                if (thisField.getJavaMember().getName().equals(superField.getJavaMember().getName())
                    && thisField.getBaseType().equals(superField.getBaseType()))
                {
                    return superField;
                }
            }
            return null;
        }
    }
    
    private static class AnnotatedMethodBeanAttributesBuilder<M> extends AnnotatedMemberBeanAttributesBuilder<M, AnnotatedMethod<M>>
    {

        protected AnnotatedMethodBeanAttributesBuilder(WebBeansContext webBeansContext, AnnotatedMethod<M> annotated)
        {
            super(webBeansContext, annotated);
        }

        @Override
        protected void defineScope()
        {
            defineScope("Annotated producer method : " + getAnnotated().getJavaMember() +  "must declare default @Scope annotation");
        }

        @Override
        protected void defineName()
        {
            if (getAnnotated().isAnnotationPresent(Specializes.class))
            {
                AnnotatedMethod<? super M> superAnnotated = getSuperAnnotated();
                defineName(superAnnotated, WebBeansUtil.getProducerDefaultName(superAnnotated.getJavaMember().getName()));
            }
            if (name == null)
            {
                defineName(getAnnotated(), WebBeansUtil.getProducerDefaultName(getAnnotated().getJavaMember().getName()));
            }
            else
            {
                // TODO XXX We have to check stereotypes here, too
                if (getAnnotated().isAnnotationPresent(Named.class))
                {
                    throw new DefinitionException("@Specialized Producer method : " + getAnnotated().getJavaMember().getName()
                            + " may not explicitly declare a bean name");
                }
            }
        }
        
        @Override
        protected void defineNullable()
        {
            defineNullable(!getAnnotated().getJavaMember().getReturnType().isPrimitive());
        }

        @Override
        protected AnnotatedMethod<? super M> getSuperAnnotated()
        {
            AnnotatedMethod<M> thisMethod = getAnnotated();
            for (AnnotatedMethod<? super M> superMethod: getSuperType().getMethods())
            {
                List<AnnotatedParameter<M>> thisParameters = thisMethod.getParameters();
                if (thisMethod.getJavaMember().getName().equals(superMethod.getJavaMember().getName())
                    && thisMethod.getBaseType().equals(superMethod.getBaseType())
                    && thisParameters.size() == superMethod.getParameters().size())
                {
                    List<AnnotatedParameter<?>> superParameters = (List<AnnotatedParameter<?>>)(List<?>)superMethod.getParameters();
                    boolean match = true;
                    for (int i = 0; i < thisParameters.size(); i++)
                    {
                        if (!thisParameters.get(i).getBaseType().equals(superParameters.get(i).getBaseType()))
                        {
                            match = false;
                            break;
                        }
                    }
                    if (match)
                    {
                        return superMethod;
                    }
                }
            }
            return null;
        }
    }

    private abstract static class AnnotatedMemberBeanAttributesBuilder<M, A extends AnnotatedMember<M>> extends BeanAttributesBuilder<M, A>
    {

        protected AnnotatedMemberBeanAttributesBuilder(WebBeansContext webBeansContext, A annotated)
        {
            super(webBeansContext, annotated);
        }

        protected AnnotatedType<? super M> getSuperType()
        {
            Class<? super M> superclass = getAnnotated().getDeclaringType().getJavaClass().getSuperclass();
            if (superclass == null)
            {
                return null;
            }
            return webBeansContext.getAnnotatedElementFactory().getAnnotatedType(superclass);
        }
    }
}