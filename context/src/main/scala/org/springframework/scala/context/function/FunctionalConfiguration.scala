/*
 * Copyright 2011-2012 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.springframework.scala.context.function

import org.springframework.util.StringUtils
import org.springframework.beans.factory.config.{BeanDefinition, ConfigurableBeanFactory}
import org.springframework.scala.beans.factory.function.{InitDestroyFunctionBeanPostProcessor, FunctionalGenericBeanDefinition}
import org.springframework.beans.factory.support.{DefaultListableBeanFactory, BeanDefinitionReaderUtils}

/**
 * @author Arjen Poutsma
 */
abstract class FunctionalConfiguration(implicit val beanFactory: DefaultListableBeanFactory) {

	/**
	 * Return an instance, which may be shared or independent, of the specified bean.
	 *
	 * Translates aliases back to the corresponding canonical bean name.
	 * Will ask the parent factory if the bean cannot be found in this factory instance.
	 * @param name the name of the bean to retrieve
	 * @param manifest an implicit ``Manifest`` representing the type of the specified
	 * type parameter.
	 * @return an instance of the bean
	 * @throws NoSuchBeanDefinitionException if there's no such bean definition
	 * @throws BeanNotOfRequiredTypeException if the bean is not of the required type
	 * @throws BeansException if the bean could not be created
	 */
	def getBean[T](name: String)(implicit manifest: Manifest[T]): T = {
		val beanType = manifest.erasure.asInstanceOf[Class[T]]
		beanFactory.getBean(name, beanType)
	}

	/**
	 * Registers a bean creation function with the given name, aliases, and other
	 * attributes.
	 *
	 * @param name the name of the bean. If not specified, a name will be generated.
	 * @param aliases aliases for the bean, if any
	 * @param scope the scope. Defaults to ``singleton``.
	 * @param lazyInit whether the bean is to be lazily initialized. Defaults to ``false``.
	 * @param beanFunction the bean creation function
	 * @return a function that returns the registered bean
	 * @tparam T the bean type
	 */
	protected def bean[T](name: String = "",
	                      aliases: Seq[String] = Seq(),
	                      scope: String = ConfigurableBeanFactory.SCOPE_SINGLETON,
	                      lazyInit: Boolean = false)
	                     (beanFunction: => T)
	                     (implicit manifest: Manifest[T]): BeanLookupFunction[T] = {

		registerBean(name, aliases, scope, lazyInit, beanFunction _, manifest)
	}

	private def registerBean[T](name: String,
	                            aliases: Seq[String],
	                            scope: String,
	                            lazyInit: Boolean,
	                            beanFunction: () => T,
	                            manifest: Manifest[T]): BeanLookupFunction[T] = {

		val beanType = manifest.erasure.asInstanceOf[Class[T]]

		val bd = new FunctionalGenericBeanDefinition(beanFunction)
		bd.setScope(scope)
		bd.setLazyInit(lazyInit)

		val beanName = getBeanName(name, bd)

		beanFactory.registerBeanDefinition(beanName, bd)
		aliases.foreach(beanFactory.registerAlias(beanName, _))

		new BeanLookupFunction[T](beanName, beanType, beanFactory)
	}

	private def getBeanName(name: String, definition: BeanDefinition): String = {
		if (StringUtils.hasLength(name)) {
			name
		}
		else {
			BeanDefinitionReaderUtils.generateBeanName(definition, beanFactory)
		}
	}

	/**
	 * Registers a singleton bean creation function with the given name, aliases, and other
	 * attributes.
	 *
	 * @param name the name of the bean. If not specified, a name will be generated.
	 * @param aliases aliases for the bean, if any
	 * @param lazyInit whether the bean is to be lazily initialized. Defaults to ``false``.
	 * @param beanFunction the bean creation function
	 * @return the singleton instance of the registered bean
	 * @tparam T the bean type
	 */
	protected def singleton[T](name: String = "",
	                           aliases: Seq[String] = Seq(),
	                           lazyInit: Boolean = false)
	                          (beanFunction: => T)
	                          (implicit manifest: Manifest[T]): T = {

		registerBean(name, aliases, ConfigurableBeanFactory.SCOPE_SINGLETON, lazyInit,
			beanFunction _, manifest).apply()
	}

	/**
	 * Registers a prototype bean creation function with the given name, aliases, and other
	 * attributes.
	 *
	 * @param name the name of the bean. If not specified, a name will be generated.
	 * @param aliases aliases for the bean, if any
	 * @param lazyInit whether the bean is to be lazily initialized. Defaults to ``false``.
	 * @param beanFunction the bean creation function
	 * @return a function that returns the registered bean
	 * @tparam T the bean type
	 */
	protected def prototype[T](name: String = "",
	                           aliases: Seq[String] = Seq(),
	                           lazyInit: Boolean = false)
	                          (beanFunction: => T)
	                          (implicit manifest: Manifest[T]): BeanLookupFunction[T] = {
		registerBean(name, aliases, ConfigurableBeanFactory.SCOPE_PROTOTYPE, lazyInit,
			beanFunction _, manifest)
	}

	/**
	 * Registers an initialization function for the given bean.
	 *
	 * @param bean the bean to register an initialization function for
	 * @param initFunction the initialization function
	 * @tparam T the bean type
	 */
	protected def init[T](bean: BeanLookupFunction[T])(initFunction: (T) => T) {
		val bpp = initDestroyFunctionBeanPostProcessor()
		bpp.registerInitFunction(bean.beanName, initFunction)
	}

	/**
	 * Registers a destruction function for the given bean.
	 *
	 * @param bean the bean to register an destruction function for
	 * @param destroyFunction the destruction function
	 * @tparam T the bean type
	 */
	protected def destroy[T](bean: BeanLookupFunction[T])(destroyFunction: (T) => Unit) {
		val bpp = initDestroyFunctionBeanPostProcessor()
		bpp.registerDestroyFunction(bean.beanName, destroyFunction)
	}

	private def initDestroyFunctionBeanPostProcessor(): InitDestroyFunctionBeanPostProcessor = {
		val bpps = beanFactory.getBeansOfType(classOf[InitDestroyFunctionBeanPostProcessor])
		assert(bpps.size() == 1)
		bpps.values().iterator().next()
	}


}