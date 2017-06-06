/*
 *  Licensed to the Apache Software Foundation (ASF) under one
 *  or more contributor license agreements.  See the NOTICE file
 *  distributed with this work for additional information
 *  regarding copyright ownership.  The ASF licenses this file
 *  to you under the Apache License, Version 2.0 (the
 *  "License"); you may not use this file except in compliance
 *  with the License.  You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing,
 *  software distributed under the License is distributed on an
 *  "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 *  KIND, either express or implied.  See the License for the
 *  specific language governing permissions and limitations
 *  under the License.
 */
package grails.testing.gorm

import grails.core.GrailsDomainClass
import grails.testing.spock.OnceBefore
import grails.validation.ConstrainedProperty
import grails.validation.ConstraintsEvaluator
import groovy.transform.CompileDynamic
import groovy.transform.CompileStatic
import org.grails.core.artefact.DomainClassArtefactHandler
import org.grails.datastore.gorm.GormEnhancer
import org.grails.datastore.gorm.validation.constraints.UniqueConstraintFactory
import org.grails.datastore.mapping.core.AbstractDatastore
import org.grails.datastore.mapping.core.DatastoreUtils
import org.grails.datastore.mapping.core.Session
import org.grails.datastore.mapping.model.PersistentEntity
import org.grails.datastore.mapping.model.lifecycle.Initializable
import org.grails.datastore.mapping.reflect.ClassPropertyFetcher
import org.grails.datastore.mapping.simple.SimpleMapDatastore
import org.grails.datastore.mapping.transactions.DatastoreTransactionManager
import org.grails.plugins.domain.DomainClassGrailsPlugin
import org.grails.testing.GrailsUnitTest
import org.grails.testing.gorm.MockCascadingDomainClassValidator
import org.grails.validation.ConstraintEvalUtils
import org.grails.validation.ConstraintsEvaluatorFactoryBean
import org.junit.After
import org.junit.AfterClass
import org.junit.Before
import org.springframework.context.ConfigurableApplicationContext
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.validation.Validator

@CompileStatic
trait DataTest extends GrailsUnitTest {

    boolean domainsHaveBeenMocked = false
    Session currentSession

    Class<?>[] getDomainClassesToMock() {}

    void mockDomain(Class<?> domainClassToMock) {
        mockDomains(domainClassToMock)
        dataStore.mappingContext.getPersistentEntity(domainClassToMock.name)
    }

    void mockDomains(Class<?>... domainClassesToMock) {
        initialMockDomainSetup()
        Collection<PersistentEntity> entities = dataStore.mappingContext.addPersistentEntities(domainClassesToMock)
        for (PersistentEntity entity in entities) {
            GrailsDomainClass domain = registerGrailsDomainClass(entity.javaClass)

            Validator validator = registerDomainClassValidator(domain)
            dataStore.mappingContext.addEntityValidator(entity, validator)
        }
        final failOnError = false //getFailOnError()
        new GormEnhancer(dataStore, transactionManager, failOnError instanceof Boolean ? (Boolean) failOnError : false)

        initializeMappingContext()
    }

    AbstractDatastore getDataStore() {
        applicationContext.getBean(AbstractDatastore)
    }

    PlatformTransactionManager getTransactionManager() {
        applicationContext.getBean('transactionManager', PlatformTransactionManager)
    }

    private GrailsDomainClass registerGrailsDomainClass(Class<?> domainClassToMock) {
        (GrailsDomainClass) grailsApplication.addArtefact(DomainClassArtefactHandler.TYPE, domainClassToMock)
    }

    @CompileDynamic
    private Validator registerDomainClassValidator(GrailsDomainClass domain) {
        String validationBeanName = "${domain.fullName}Validator"
        defineBeans {
            "${domain.fullName}"(domain.clazz) { bean ->
                bean.singleton = false
                bean.autowire = "byName"
            }
            "$validationBeanName"(MockCascadingDomainClassValidator) { bean ->
                getDelegate().messageSource = ref("messageSource")
                bean.lazyInit = true
                getDelegate().domainClass = domain
                getDelegate().grailsApplication = grailsApplication
            }
        }

        applicationContext.getBean(validationBeanName, Validator)
    }

    private void initialMockDomainSetup() {
        ConstraintEvalUtils.clearDefaultConstraints()
        ((DomainClassArtefactHandler) grailsApplication.getArtefactHandler(DomainClassArtefactHandler.TYPE)).setGrailsApplication(grailsApplication)
    }

    private void initializeMappingContext() {
        def context = dataStore.mappingContext
        if (context instanceof Initializable) {
            context.initialize()
        }
    }
}
