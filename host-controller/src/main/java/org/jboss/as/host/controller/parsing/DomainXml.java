/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2011, Red Hat, Inc., and individual contributors
 * as indicated by the @author tags. See the copyright.txt file in the
 * distribution for a full listing of individual contributors.
 *
 * This is free software; you can redistribute it and/or modify it
 * under the terms of the GNU Lesser General Public License as
 * published by the Free Software Foundation; either version 2.1 of
 * the License, or (at your option) any later version.
 *
 * This software is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this software; if not, write to the Free
 * Software Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA
 * 02110-1301 USA, or see the FSF site: http://www.fsf.org.
 */
package org.jboss.as.host.controller.parsing;

import static javax.xml.stream.XMLStreamConstants.END_ELEMENT;
import static org.jboss.as.controller.ControllerMessages.MESSAGES;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.ACCESS;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.ADD;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.AUTHORIZATION;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.CONTENT;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.CORE_SERVICE;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.DEPLOYMENT;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.DEPLOYMENT_OVERLAY;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.EXTENSION;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.HASH;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.INCLUDE;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.INCLUDES;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.INTERFACE;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.JVM;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.MANAGEMENT;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.MANAGEMENT_CLIENT_CONTENT;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.NAME;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.OP;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.OP_ADDR;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.PATH;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.PROFILE;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.ROLLOUT_PLANS;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.SERVER_GROUP;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.SOCKET_BINDING_GROUP;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.SOCKET_BINDING_PORT_OFFSET;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.SUBSYSTEM;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.SYSTEM_PROPERTY;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.VALUE;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.WRITE_ATTRIBUTE_OPERATION;
import static org.jboss.as.controller.parsing.ParseUtils.isNoNamespaceAttribute;
import static org.jboss.as.controller.parsing.ParseUtils.missingRequired;
import static org.jboss.as.controller.parsing.ParseUtils.nextElement;
import static org.jboss.as.controller.parsing.ParseUtils.readStringAttributeElement;
import static org.jboss.as.controller.parsing.ParseUtils.requireAttributes;
import static org.jboss.as.controller.parsing.ParseUtils.requireNamespace;
import static org.jboss.as.controller.parsing.ParseUtils.requireNoAttributes;
import static org.jboss.as.controller.parsing.ParseUtils.requireSingleAttribute;
import static org.jboss.as.controller.parsing.ParseUtils.unexpectedAttribute;
import static org.jboss.as.controller.parsing.ParseUtils.unexpectedElement;
import static org.jboss.as.domain.controller.DomainControllerLogger.HOST_CONTROLLER_LOGGER;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;

import javax.xml.XMLConstants;
import javax.xml.stream.XMLStreamException;

import org.jboss.as.controller.HashUtil;
import org.jboss.as.controller.descriptions.ModelDescriptionConstants;
import org.jboss.as.controller.extension.ExtensionRegistry;
import org.jboss.as.controller.operations.common.Util;
import org.jboss.as.controller.parsing.Attribute;
import org.jboss.as.controller.parsing.Element;
import org.jboss.as.controller.parsing.ExtensionXml;
import org.jboss.as.controller.parsing.Namespace;
import org.jboss.as.controller.parsing.ParseUtils;
import org.jboss.as.controller.parsing.ProfileParsingCompletionHandler;
import org.jboss.as.controller.persistence.ModelMarshallingContext;
import org.jboss.as.controller.persistence.SubsystemMarshallingContext;
import org.jboss.as.controller.resource.SocketBindingGroupResourceDefinition;
import org.jboss.as.domain.controller.resources.DomainRootDefinition;
import org.jboss.as.domain.controller.resources.ServerGroupResourceDefinition;
import org.jboss.as.domain.management.access.AccessAuthorizationResourceDefinition;
import org.jboss.as.domain.management.parsing.ManagementXml;
import org.jboss.as.server.controller.resources.DeploymentAttributes;
import org.jboss.as.server.parsing.CommonXml;
import org.jboss.dmr.ModelNode;
import org.jboss.dmr.ModelType;
import org.jboss.dmr.Property;
import org.jboss.modules.ModuleLoader;
import org.jboss.staxmapper.XMLElementWriter;
import org.jboss.staxmapper.XMLExtendedStreamReader;
import org.jboss.staxmapper.XMLExtendedStreamWriter;

/**
 * A mapper between an AS server's configuration model and XML representations, particularly {@code domain.xml}.
 *
 * @author Emanuel Muckenhuber
 * @author <a href="mailto:darran.lofthouse@jboss.com">Darran Lofthouse</a>
 */
public class DomainXml extends CommonXml {

    private final ExtensionXml extensionXml;
    private final ExtensionRegistry extensionRegistry;

    public DomainXml(final ModuleLoader loader, ExecutorService executorService, ExtensionRegistry extensionRegistry) {
        super();
        extensionXml = new ExtensionXml(loader, executorService, extensionRegistry);
        this.extensionRegistry = extensionRegistry;
    }

    @Override
    public void readElement(final XMLExtendedStreamReader reader, final List<ModelNode> nodes) throws XMLStreamException {
        if (Element.forName(reader.getLocalName()) != Element.DOMAIN) {
            throw unexpectedElement(reader);
        }
        Namespace readerNS = Namespace.forUri(reader.getNamespaceURI());
        switch (readerNS) {
            case DOMAIN_1_0:
                readDomainElement1_0(reader, new ModelNode(), readerNS, nodes);
                break;
            case DOMAIN_1_1:
            case DOMAIN_1_2:
                readDomainElement1_1(reader, new ModelNode(), readerNS, nodes);
                break;
            case DOMAIN_1_3:
                readDomainElement1_3(reader, new ModelNode(), readerNS, nodes);
                break;
            case DOMAIN_1_4:
                readDomainElement1_4(reader, new ModelNode(), readerNS, nodes);
                break;
            default:
                // Instead of having to list the remaining versions we just check it is actually a valid version.
                for (Namespace current : Namespace.domainValues()) {
                    if (readerNS.equals(current)) {
                        readDomainElement2_0(reader, new ModelNode(), readerNS, nodes);
                        return;
                    }
                }
                throw unexpectedElement(reader);
        }
    }

    @Override
    public void writeContent(final XMLExtendedStreamWriter writer, final ModelMarshallingContext context) throws XMLStreamException {
        ModelNode modelNode = context.getModelNode();

        writer.writeStartDocument();
        writer.writeStartElement(Element.DOMAIN.getLocalName());

        writer.writeDefaultNamespace(Namespace.CURRENT.getUriString());
        writeNamespaces(writer, modelNode);
        writeSchemaLocation(writer, modelNode);

        DomainRootDefinition.NAME.marshallAsAttribute(modelNode, false, writer);

        writeNewLine(writer);

        if (modelNode.hasDefined(EXTENSION)) {
            extensionXml.writeExtensions(writer, modelNode.get(EXTENSION));
            writeNewLine(writer);
        }
        if (modelNode.hasDefined(SYSTEM_PROPERTY)) {
            writeProperties(writer, modelNode.get(SYSTEM_PROPERTY), Element.SYSTEM_PROPERTIES, false);
            writeNewLine(writer);
        }
        if (modelNode.hasDefined(PATH)) {
            writePaths(writer, modelNode.get(PATH), true);
            writeNewLine(writer);
        }

        if (modelNode.hasDefined(CORE_SERVICE) && modelNode.get(CORE_SERVICE).hasDefined(MANAGEMENT)) {
            ManagementXml managementXml = new ManagementXml(new ManagementXmlDelegate());
            managementXml.writeManagement(writer, modelNode.get(CORE_SERVICE, MANAGEMENT), true);
            writeNewLine(writer);
        }

        if (modelNode.hasDefined(PROFILE)) {
            writer.writeStartElement(Element.PROFILES.getLocalName());
            for (final Property profile : modelNode.get(PROFILE).asPropertyList()) {
                writeProfile(writer, profile.getName(), profile.getValue(), context);
            }
            writer.writeEndElement();
            writeNewLine(writer);
        }
        if (modelNode.hasDefined(INTERFACE)) {
            writeInterfaces(writer, modelNode.get(INTERFACE));
            writeNewLine(writer);
        }
        if (modelNode.hasDefined(SOCKET_BINDING_GROUP)) {
            writer.writeStartElement(Element.SOCKET_BINDING_GROUPS.getLocalName());
            for (final Property property : modelNode.get(SOCKET_BINDING_GROUP).asPropertyList()) {
                writeSocketBindingGroup(writer, property.getValue(), false);
            }
            writer.writeEndElement();
            writeNewLine(writer);
        }
        if (modelNode.hasDefined(DEPLOYMENT)) {
            writeDomainDeployments(writer, modelNode.get(DEPLOYMENT));
            writeNewLine(writer);
        }
        if (modelNode.hasDefined(DEPLOYMENT_OVERLAY)) {
            writeDeploymentOverlays(writer, modelNode.get(DEPLOYMENT_OVERLAY));
            writeNewLine(writer);
        }
        if (modelNode.hasDefined(SERVER_GROUP)) {
            writer.writeStartElement(Element.SERVER_GROUPS.getLocalName());
            for (final Property property : modelNode.get(SERVER_GROUP).asPropertyList()) {
                writeServerGroup(writer, property.getName(), property.getValue());
            }
            writer.writeEndElement();
            writeNewLine(writer);
        }
        if (modelNode.hasDefined(MANAGEMENT_CLIENT_CONTENT)) {
            writeManagementClientContent(writer, modelNode.get(MANAGEMENT_CLIENT_CONTENT));
            writeNewLine(writer);
        }

        writer.writeEndElement();
        writeNewLine(writer);
        writer.writeEndDocument();
    }

    void readDomainElement1_0(final XMLExtendedStreamReader reader, final ModelNode address, final Namespace expectedNs, final List<ModelNode> list) throws XMLStreamException {

        parseNamespaces(reader, address, list);

        // attributes
        readDomainElementAttributes_1_0(reader, address, list);

        // Content
        // Handle elements: sequence
        Element element = nextElement(reader, expectedNs);
        if (element == Element.EXTENSIONS) {
            extensionXml.parseExtensions(reader, address, expectedNs, list);
            element = nextElement(reader, expectedNs);
        }
        if (element == Element.SYSTEM_PROPERTIES) {
            parseSystemProperties(reader, address, expectedNs, list, false);
            element = nextElement(reader, expectedNs);
        }
        if (element == Element.PATHS) {
            parsePaths(reader, address, expectedNs, list, false);
            element = nextElement(reader, expectedNs);
        }
        if (element == Element.PROFILES) {
            parseProfiles(reader, address, expectedNs, list);
            element = nextElement(reader, expectedNs);
        }
        final Set<String> interfaceNames = new HashSet<String>();
        if (element == Element.INTERFACES) {
            parseInterfaces(reader, interfaceNames, address, expectedNs, list, false);
            element = nextElement(reader, expectedNs);
        }
        if (element == Element.SOCKET_BINDING_GROUPS) {
            parseDomainSocketBindingGroups(reader, address, expectedNs, list, interfaceNames);
            element = nextElement(reader, expectedNs);
        }
        if (element == Element.DEPLOYMENTS) {
            parseDeployments(reader, address, expectedNs, list, EnumSet.of(Attribute.NAME, Attribute.RUNTIME_NAME),
                    EnumSet.of(Element.CONTENT, Element.FS_ARCHIVE, Element.FS_EXPLODED));
            element = nextElement(reader, expectedNs);
        }
        if (element == Element.SERVER_GROUPS) {
            parseServerGroups(reader, address, expectedNs, list);
            element = nextElement(reader, expectedNs);
        }
        if (element != null) {
            throw unexpectedElement(reader);
        }
        // Always add op(s) to set up management-client-content resources
        initializeRolloutPlans(address, list);
    }

    void readDomainElement1_1(final XMLExtendedStreamReader reader, final ModelNode address, final Namespace expectedNs, final List<ModelNode> list) throws XMLStreamException {

        parseNamespaces(reader, address, list);

        // attributes
        readDomainElementAttributes_1_1(reader, address, list);

        // Content
        // Handle elements: sequence
        Element element = nextElement(reader, expectedNs);
        if (element == Element.EXTENSIONS) {
            extensionXml.parseExtensions(reader, address, expectedNs, list);
            element = nextElement(reader, expectedNs);
        }
        if (element == Element.SYSTEM_PROPERTIES) {
            parseSystemProperties(reader, address, expectedNs, list, false);
            element = nextElement(reader, expectedNs);
        }
        if (element == Element.PATHS) {
            parsePaths(reader, address, expectedNs, list, false);
            element = nextElement(reader, expectedNs);
        }
        if (element == Element.PROFILES) {
            parseProfiles(reader, address, expectedNs, list);
            element = nextElement(reader, expectedNs);
        }
        final Set<String> interfaceNames = new HashSet<String>();
        if (element == Element.INTERFACES) {
            parseInterfaces(reader, interfaceNames, address, expectedNs, list, false);
            element = nextElement(reader, expectedNs);
        }
        if (element == Element.SOCKET_BINDING_GROUPS) {
            parseDomainSocketBindingGroups(reader, address, expectedNs, list, interfaceNames);
            element = nextElement(reader, expectedNs);
        }
        if (element == Element.DEPLOYMENTS) {
            parseDeployments(reader, address, expectedNs, list, EnumSet.of(Attribute.NAME, Attribute.RUNTIME_NAME),
                    EnumSet.of(Element.CONTENT, Element.FS_ARCHIVE, Element.FS_EXPLODED));
            element = nextElement(reader, expectedNs);
        }
        if (element == Element.SERVER_GROUPS) {
            parseServerGroups(reader, address, expectedNs, list);
            element = nextElement(reader, expectedNs);
        }
        if (element == Element.MANAGEMENT_CLIENT_CONTENT) {
            parseManagementClientContent(reader, address, expectedNs, list);
            element = nextElement(reader, expectedNs);
        } else if (element == null) {
            // Always add op(s) to set up management-client-content resources
            initializeRolloutPlans(address, list);
        } else {
            throw unexpectedElement(reader);
        }
    }

    void readDomainElement1_3(final XMLExtendedStreamReader reader, final ModelNode address, final Namespace expectedNs, final List<ModelNode> list) throws XMLStreamException {

        parseNamespaces(reader, address, list);

        // attributes
        readDomainElementAttributes_1_3(reader, expectedNs, address, list);

        // Content
        // Handle elements: sequence
        Element element = nextElement(reader, expectedNs);
        if (element == Element.EXTENSIONS) {
            extensionXml.parseExtensions(reader, address, expectedNs, list);
            element = nextElement(reader, expectedNs);
        }
        if (element == Element.SYSTEM_PROPERTIES) {
            parseSystemProperties(reader, address, expectedNs, list, false);
            element = nextElement(reader, expectedNs);
        }
        if (element == Element.PATHS) {
            parsePaths(reader, address, expectedNs, list, false);
            element = nextElement(reader, expectedNs);
        }
        if (element == Element.PROFILES) {
            parseProfiles(reader, address, expectedNs, list);
            element = nextElement(reader, expectedNs);
        }
        final Set<String> interfaceNames = new HashSet<String>();
        if (element == Element.INTERFACES) {
            parseInterfaces(reader, interfaceNames, address, expectedNs, list, false);
            element = nextElement(reader, expectedNs);
        }
        if (element == Element.SOCKET_BINDING_GROUPS) {
            parseDomainSocketBindingGroups(reader, address, expectedNs, list, interfaceNames);
            element = nextElement(reader, expectedNs);
        }
        if (element == Element.DEPLOYMENTS) {
            parseDeployments(reader, address, expectedNs, list, EnumSet.of(Attribute.NAME, Attribute.RUNTIME_NAME),
                    EnumSet.of(Element.CONTENT, Element.FS_ARCHIVE, Element.FS_EXPLODED));
            element = nextElement(reader, expectedNs);
        }
        if (element == Element.SERVER_GROUPS) {
            parseServerGroups(reader, address, expectedNs, list);
            element = nextElement(reader, expectedNs);
        }
        if (element == Element.MANAGEMENT_CLIENT_CONTENT) {
            parseManagementClientContent(reader, address, expectedNs, list);
            element = nextElement(reader, expectedNs);
        } else if (element == null) {
            // Always add op(s) to set up management-client-content resources
            initializeRolloutPlans(address, list);
        } else {
            throw unexpectedElement(reader);
        }
    }

    void readDomainElement1_4(final XMLExtendedStreamReader reader, final ModelNode address, final Namespace expectedNs, final List<ModelNode> list) throws XMLStreamException {

        parseNamespaces(reader, address, list);

        // attributes
        readDomainElementAttributes_1_3(reader, expectedNs, address, list);

        // Content
        // Handle elements: sequence
        Element element = nextElement(reader, expectedNs);
        if (element == Element.EXTENSIONS) {
            extensionXml.parseExtensions(reader, address, expectedNs, list);
            element = nextElement(reader, expectedNs);
        }
        if (element == Element.SYSTEM_PROPERTIES) {
            parseSystemProperties(reader, address, expectedNs, list, false);
            element = nextElement(reader, expectedNs);
        }
        if (element == Element.PATHS) {
            parsePaths(reader, address, expectedNs, list, false);
            element = nextElement(reader, expectedNs);
        }
        if (element == Element.PROFILES) {
            parseProfiles(reader, address, expectedNs, list);
            element = nextElement(reader, expectedNs);
        }
        final Set<String> interfaceNames = new HashSet<String>();
        if (element == Element.INTERFACES) {
            parseInterfaces(reader, interfaceNames, address, expectedNs, list, false);
            element = nextElement(reader, expectedNs);
        }
        if (element == Element.SOCKET_BINDING_GROUPS) {
            parseDomainSocketBindingGroups(reader, address, expectedNs, list, interfaceNames);
            element = nextElement(reader, expectedNs);
        }
        if (element == Element.DEPLOYMENTS) {
            parseDeployments(reader, address, expectedNs, list, EnumSet.of(Attribute.NAME, Attribute.RUNTIME_NAME),
                    EnumSet.of(Element.CONTENT, Element.FS_ARCHIVE, Element.FS_EXPLODED));
            element = nextElement(reader, expectedNs);
        }
        if (element == Element.DEPLOYMENT_OVERLAYS) {
            parseDeploymentOverlays(reader, expectedNs, new ModelNode(), list, true, false);
            element = nextElement(reader, expectedNs);
        }
        if (element == Element.SERVER_GROUPS) {
            parseServerGroups(reader, address, expectedNs, list);
            element = nextElement(reader, expectedNs);
        }
        if (element == Element.MANAGEMENT_CLIENT_CONTENT) {
            parseManagementClientContent(reader, address, expectedNs, list);
            element = nextElement(reader, expectedNs);
        } else if (element == null) {
            // Always add op(s) to set up management-client-content resources
            initializeRolloutPlans(address, list);
        } else {
            throw unexpectedElement(reader);
        }
    }

    void readDomainElement2_0(final XMLExtendedStreamReader reader, final ModelNode address, final Namespace expectedNs, final List<ModelNode> list) throws XMLStreamException {

        parseNamespaces(reader, address, list);

        // attributes
        readDomainElementAttributes_1_3(reader, expectedNs, address, list);

        // Content
        // Handle elements: sequence
        Element element = nextElement(reader, expectedNs);
        if (element == Element.EXTENSIONS) {
            extensionXml.parseExtensions(reader, address, expectedNs, list);
            element = nextElement(reader, expectedNs);
        }
        if (element == Element.SYSTEM_PROPERTIES) {
            parseSystemProperties(reader, address, expectedNs, list, false);
            element = nextElement(reader, expectedNs);
        }
        if (element == Element.PATHS) {
            parsePaths(reader, address, expectedNs, list, false);
            element = nextElement(reader, expectedNs);
        }
        if (element == Element.MANAGEMENT) {
            ManagementXml managementXml = new ManagementXml(new ManagementXmlDelegate());
            managementXml.parseManagement(reader, address, expectedNs, list, false);
            element = nextElement(reader, expectedNs);
        }
        if (element == Element.PROFILES) {
            parseProfiles(reader, address, expectedNs, list);
            element = nextElement(reader, expectedNs);
        }
        final Set<String> interfaceNames = new HashSet<String>();
        if (element == Element.INTERFACES) {
            parseInterfaces(reader, interfaceNames, address, expectedNs, list, false);
            element = nextElement(reader, expectedNs);
        }
        if (element == Element.SOCKET_BINDING_GROUPS) {
            parseDomainSocketBindingGroups(reader, address, expectedNs, list, interfaceNames);
            element = nextElement(reader, expectedNs);
        }
        if (element == Element.DEPLOYMENTS) {
            parseDeployments(reader, address, expectedNs, list, EnumSet.of(Attribute.NAME, Attribute.RUNTIME_NAME),
                    EnumSet.of(Element.CONTENT, Element.FS_ARCHIVE, Element.FS_EXPLODED));
            element = nextElement(reader, expectedNs);
        }
        if (element == Element.DEPLOYMENT_OVERLAYS) {
            parseDeploymentOverlays(reader, expectedNs, new ModelNode(), list, true, false);
            element = nextElement(reader, expectedNs);
        }
        if (element == Element.SERVER_GROUPS) {
            parseServerGroups(reader, address, expectedNs, list);
            element = nextElement(reader, expectedNs);
        }
        if (element == Element.MANAGEMENT_CLIENT_CONTENT) {
            parseManagementClientContent(reader, address, expectedNs, list);
            element = nextElement(reader, expectedNs);
        } else if (element == null) {
            // Always add op(s) to set up management-client-content resources
            initializeRolloutPlans(address, list);
        } else {
            throw unexpectedElement(reader);
        }
    }

    protected void readDomainElementAttributes_1_0(XMLExtendedStreamReader reader, ModelNode address, List<ModelNode> list) throws XMLStreamException {
        final int count = reader.getAttributeCount();
        for (int i = 0; i < count; i++) {
            switch (Namespace.forUri(reader.getAttributeNamespace(i))) {
                case XML_SCHEMA_INSTANCE: {
                    switch (Attribute.forName(reader.getAttributeLocalName(i))) {
                        case SCHEMA_LOCATION: {
                            parseSchemaLocations(reader, address, list, i);
                            break;
                        }
                        case NO_NAMESPACE_SCHEMA_LOCATION: {
                            // todo, jeez
                            break;
                        }
                        default: {
                            throw unexpectedAttribute(reader, i);
                        }
                    }
                    break;
                }
                default:
                    throw unexpectedAttribute(reader, i);
            }
        }
    }

    protected void readDomainElementAttributes_1_1(XMLExtendedStreamReader reader, ModelNode address, List<ModelNode> list) throws XMLStreamException {
        readDomainElementAttributes_1_0(reader, address, list);
    }

    protected void readDomainElementAttributes_1_3(XMLExtendedStreamReader reader, Namespace expectedNs, ModelNode address, List<ModelNode> list) throws XMLStreamException {
        final int count = reader.getAttributeCount();
        for (int i = 0; i < count; i++) {
            Namespace ns = Namespace.forUri(reader.getAttributeNamespace(i));
            switch (ns) {
                case XML_SCHEMA_INSTANCE: {
                    switch (Attribute.forName(reader.getAttributeLocalName(i))) {
                        case SCHEMA_LOCATION: {
                            parseSchemaLocations(reader, address, list, i);
                            break;
                        }
                        case NO_NAMESPACE_SCHEMA_LOCATION: {
                            // todo, jeez
                            break;
                        }
                        default: {
                            throw unexpectedAttribute(reader, i);
                        }
                    }
                    break;
                }
                default:
                    switch (Attribute.forName(reader.getAttributeLocalName(i))) {
                    case NAME:
                        ModelNode op = new ModelNode();
                        op.get(OP).set(WRITE_ATTRIBUTE_OPERATION);
                        op.get(NAME).set(NAME);
                        op.get(VALUE).set(ParseUtils.parsePossibleExpression(reader.getAttributeValue(i)));
                        list.add(op);
                        break;
                    default:
                        throw unexpectedAttribute(reader, i);
                }
            }
        }
    }

    void parseDomainSocketBindingGroups(final XMLExtendedStreamReader reader, final ModelNode address, final Namespace expectedNs,
            final List<ModelNode> list, final Set<String> interfaces) throws XMLStreamException {
        while (reader.hasNext() && reader.nextTag() != END_ELEMENT) {
            requireNamespace(reader, expectedNs);
            final Element element = Element.forName(reader.getLocalName());
            switch (element) {
                case SOCKET_BINDING_GROUP: {
                    switch (expectedNs) {
                        case DOMAIN_1_0:
                            // parse 1.0 socket binding group
                            this.parseSocketBindingGroup_1_0(reader, interfaces, address, expectedNs, list);
                            break;
                        default:
                            // parse 1.1 socket binding group
                            this.parseSocketBindingGroup_1_1(reader, interfaces, address, expectedNs, list);
                            break;
                    }
                    break;
                }
                default: {
                    throw unexpectedElement(reader);
                }
            }
        }
    }

    void parseSocketBindingGroup_1_0(final XMLExtendedStreamReader reader, final Set<String> interfaces, final ModelNode address,
            final Namespace expectedNs, final List<ModelNode> updates) throws XMLStreamException {
        final Set<String> includedGroups = new HashSet<String>();
        // unique socket-binding names
        final Set<String> uniqueBindingNames = new HashSet<String>();

        // Handle attributes
        final String[] attrValues = requireAttributes(reader, Attribute.NAME.getLocalName(), Attribute.DEFAULT_INTERFACE.getLocalName());
        final String socketBindingGroupName = attrValues[0];
        final String defaultInterface = attrValues[1];

        final ModelNode groupAddress = new ModelNode().set(address);
        groupAddress.add(SOCKET_BINDING_GROUP, socketBindingGroupName);

        final ModelNode bindingGroupUpdate = new ModelNode();
        bindingGroupUpdate.get(OP_ADDR).set(groupAddress);
        bindingGroupUpdate.get(OP).set(ADD);

        SocketBindingGroupResourceDefinition.DEFAULT_INTERFACE.parseAndSetParameter(defaultInterface, bindingGroupUpdate, reader);
        if (bindingGroupUpdate.get(SocketBindingGroupResourceDefinition.DEFAULT_INTERFACE.getName()).getType() != ModelType.EXPRESSION
                && !interfaces.contains(defaultInterface)) {
            throw MESSAGES.unknownInterface(defaultInterface, Attribute.DEFAULT_INTERFACE.getLocalName(),
                    Element.INTERFACES.getLocalName(), reader.getLocation());
        }

        final ModelNode includes = bindingGroupUpdate.get(INCLUDES);
        includes.setEmptyList();
        updates.add(bindingGroupUpdate);

        // Handle elements
        while (reader.nextTag() != END_ELEMENT) {
            requireNamespace(reader, expectedNs);
            final Element element = Element.forName(reader.getLocalName());
            switch (element) {
                case INCLUDE: {
                    HOST_CONTROLLER_LOGGER.warnIgnoringSocketBindingGroupInclude(reader.getLocation());

                    /* This will be reintroduced for 7.2.0, leave commented out
                     final String includedGroup = readStringAttributeElement(reader, Attribute.SOCKET_BINDING_GROUP.getLocalName());
                     if (!includedGroups.add(includedGroup)) {
                     throw MESSAGES.alreadyDeclared(Attribute.SOCKET_BINDING_GROUP.getLocalName(), includedGroup, reader.getLocation());
                     }
                     SocketBindingGroupResourceDefinition.INCLUDES.parseAndAddParameterElement(includedGroup, bindingGroupUpdate, reader.getLocation());
                     */
                    break;
                }
                case SOCKET_BINDING: {
                    final String bindingName = parseSocketBinding(reader, interfaces, groupAddress, updates);
                    if (!uniqueBindingNames.add(bindingName)) {
                        throw MESSAGES.alreadyDeclared(Element.SOCKET_BINDING.getLocalName(), bindingName,
                                Element.SOCKET_BINDING_GROUP.getLocalName(), socketBindingGroupName, reader.getLocation());
                    }
                    break;
                }
                default:
                    throw unexpectedElement(reader);
            }
        }
    }

    void parseSocketBindingGroup_1_1(final XMLExtendedStreamReader reader, final Set<String> interfaces, final ModelNode address,
            final Namespace expectedNs, final List<ModelNode> updates) throws XMLStreamException {
        final Set<String> includedGroups = new HashSet<String>();
        // both outbound-socket-bindings and socket-binding names
        final Set<String> uniqueBindingNames = new HashSet<String>();

        // Handle attributes
        final String[] attrValues = requireAttributes(reader, Attribute.NAME.getLocalName(), Attribute.DEFAULT_INTERFACE.getLocalName());
        final String socketBindingGroupName = attrValues[0];
        final String defaultInterface = attrValues[1];

        final ModelNode groupAddress = new ModelNode().set(address);
        groupAddress.add(SOCKET_BINDING_GROUP, socketBindingGroupName);

        final ModelNode bindingGroupUpdate = new ModelNode();
        bindingGroupUpdate.get(OP_ADDR).set(groupAddress);
        bindingGroupUpdate.get(OP).set(ADD);

        SocketBindingGroupResourceDefinition.DEFAULT_INTERFACE.parseAndSetParameter(defaultInterface, bindingGroupUpdate, reader);
        if (bindingGroupUpdate.get(SocketBindingGroupResourceDefinition.DEFAULT_INTERFACE.getName()).getType() != ModelType.EXPRESSION
                && !interfaces.contains(defaultInterface)) {
            throw MESSAGES.unknownInterface(defaultInterface, Attribute.DEFAULT_INTERFACE.getLocalName(),
                    Element.INTERFACES.getLocalName(), reader.getLocation());
        }

        /*This will be reintroduced for 7.2.0, leave commented out
         final ModelNode includes = bindingGroupUpdate.get(INCLUDES);
         includes.setEmptyList();
         */
        updates.add(bindingGroupUpdate);

        // Handle elements
        while (reader.nextTag() != END_ELEMENT) {
            requireNamespace(reader, expectedNs);
            final Element element = Element.forName(reader.getLocalName());
            switch (element) {
                /* This will be reintroduced for 7.2.0, leave commented out
                 case INCLUDE: {
                 final String includedGroup = readStringAttributeElement(reader, Attribute.SOCKET_BINDING_GROUP.getLocalName());
                 if (!includedGroups.add(includedGroup)) {
                 throw MESSAGES.alreadyDeclared(Attribute.SOCKET_BINDING_GROUP.getLocalName(), includedGroup, reader.getLocation());
                 }
                 SocketBindingGroupResourceDefinition.INCLUDES.parseAndAddParameterElement(includedGroup, bindingGroupUpdate, reader.getLocation());
                 break;
                 }
                 */
                case SOCKET_BINDING: {
                    final String bindingName = parseSocketBinding(reader, interfaces, groupAddress, updates);
                    if (!uniqueBindingNames.add(bindingName)) {
                        throw MESSAGES.alreadyDeclared(Element.SOCKET_BINDING.getLocalName(), Element.OUTBOUND_SOCKET_BINDING.getLocalName(),
                                bindingName, Element.SOCKET_BINDING_GROUP.getLocalName(), socketBindingGroupName, reader.getLocation());
                    }
                    break;
                }
                case OUTBOUND_SOCKET_BINDING: {
                    final String bindingName = parseOutboundSocketBinding(reader, interfaces, groupAddress, updates);
                    if (!uniqueBindingNames.add(bindingName)) {
                        throw MESSAGES.alreadyDeclared(Element.SOCKET_BINDING.getLocalName(), Element.OUTBOUND_SOCKET_BINDING.getLocalName(),
                                bindingName, Element.SOCKET_BINDING_GROUP.getLocalName(), socketBindingGroupName, reader.getLocation());
                    }
                    break;
                }
                default:
                    throw unexpectedElement(reader);
            }
        }
    }

    void parseServerGroups(final XMLExtendedStreamReader reader, final ModelNode address, final Namespace expectedNs, final List<ModelNode> list) throws XMLStreamException {
        requireNoAttributes(reader);

        final Set<String> names = new HashSet<String>();

        while (reader.nextTag() != END_ELEMENT) {
            requireNamespace(reader, expectedNs);
            Element serverGroup = Element.forName(reader.getLocalName());
            if (Element.SERVER_GROUP != serverGroup) {
                throw unexpectedElement(reader);
            }

            final ModelNode groupAddOp = new ModelNode();
            groupAddOp.get(OP).set(ADD);
            groupAddOp.get(OP_ADDR);

            String name = null;

            // Handle attributes
            Set<Attribute> required = EnumSet.of(Attribute.NAME, Attribute.PROFILE);
            final int count = reader.getAttributeCount();
            for (int i = 0; i < count; i++) {

                final String value = reader.getAttributeValue(i);
                if (!isNoNamespaceAttribute(reader, i)) {
                    throw ParseUtils.unexpectedAttribute(reader, i);
                } else {
                    final Attribute attribute = Attribute.forName(reader.getAttributeLocalName(i));
                    required.remove(attribute);
                    switch (attribute) {
                        case NAME: {
                            if (!names.add(value)) {
                                throw ParseUtils.duplicateNamedElement(reader, value);
                            }
                            name = value;
                            break;
                        }
                        case PROFILE: {
                            ServerGroupResourceDefinition.PROFILE.parseAndSetParameter(value, groupAddOp, reader);
                            break;
                        }
                        case MANAGEMENT_SUBSYSTEM_ENDPOINT: {
                            ServerGroupResourceDefinition.MANAGEMENT_SUBSYSTEM_ENDPOINT.parseAndSetParameter(value, groupAddOp, reader);
                            break;
                        }
                        default:
                            throw ParseUtils.unexpectedAttribute(reader, i);
                    }
                }
            }
            if (!required.isEmpty()) {
                throw missingRequired(reader, required);
            }

            final ModelNode groupAddress = new ModelNode().set(address);
            groupAddress.add(ModelDescriptionConstants.SERVER_GROUP, name);
            groupAddOp.get(OP_ADDR).set(groupAddress);

            list.add(groupAddOp);

            // Handle elements
            boolean sawDeployments = false;

            while (reader.hasNext() && reader.nextTag() != END_ELEMENT) {
                requireNamespace(reader, expectedNs);
                final Element element = Element.forName(reader.getLocalName());
                switch (element) {
                    case JVM: {
                        JvmXml.parseJvm(reader, groupAddress, expectedNs, list, new HashSet<String>());
                        break;
                    }
                    case SOCKET_BINDING_GROUP: {
                        parseSocketBindingGroupRef(reader, groupAddOp, ServerGroupResourceDefinition.SOCKET_BINDING_GROUP,
                                ServerGroupResourceDefinition.SOCKET_BINDING_PORT_OFFSET);
                        break;
                    }
                    case DEPLOYMENTS: {
                        if (sawDeployments) {
                            throw MESSAGES.alreadyDefined(element.getLocalName(), reader.getLocation());
                        }
                        sawDeployments = true;
                        List<ModelNode> deployments = new ArrayList<ModelNode>();
                        parseDeployments(reader, groupAddress, expectedNs, deployments, EnumSet.of(Attribute.NAME, Attribute.RUNTIME_NAME, Attribute.ENABLED), Collections.<Element>emptySet());
                        checkDeployments(deployments, reader);
                        list.addAll(deployments);
                        break;
                    }
                    case DEPLOYMENT_OVERLAYS: {
                        parseDeploymentOverlays(reader, expectedNs, groupAddress, list, false, true);
                        break;
                    }
                    case SYSTEM_PROPERTIES: {
                        parseSystemProperties(reader, groupAddress, expectedNs, list, false);
                        break;
                    }
                    default:
                        throw ParseUtils.unexpectedElement(reader);
                }
            }

        }
    }

    private void checkDeployments(List<ModelNode> deployments, final XMLExtendedStreamReader reader) throws XMLStreamException {
        final Set<String> uniqueDeploymentNames = new HashSet<String>(deployments.size());
        final Set<String> uniqueRuntimeNames = new HashSet<String>(deployments.size());
        for (ModelNode deployment : deployments) {
            String deploymentName = null;
            for (Property property : deployment.get(ModelDescriptionConstants.ADDRESS).asPropertyList()) {
                if (ModelDescriptionConstants.DEPLOYMENT.equals(property.getName())) {
                    deploymentName = property.getValue().asString();
                }
            }
            if (!uniqueDeploymentNames.add(deploymentName)) {
                throw ParseUtils.duplicateNamedElement(reader, deploymentName);
            }
            String runtimeName = deployment.get(ModelDescriptionConstants.RUNTIME_NAME).asString();
            if (!uniqueRuntimeNames.add(runtimeName)) {
                throw ParseUtils.duplicateNamedElement(reader, runtimeName);
            }
        }
    }

    void parseProfiles(final XMLExtendedStreamReader reader, final ModelNode address, final Namespace expectedNs, final List<ModelNode> list) throws XMLStreamException {
        requireNoAttributes(reader);

        final Set<String> names = new HashSet<String>();

        while (reader.nextTag() != END_ELEMENT) {
            requireNamespace(reader, expectedNs);
            Element element = Element.forName(reader.getLocalName());
            if (Element.PROFILE != element) {
                throw unexpectedElement(reader);
            }

            // Attributes
            requireSingleAttribute(reader, Attribute.NAME.getLocalName());
            final String name = reader.getAttributeValue(0);
            if (!names.add(name)) {
                throw MESSAGES.duplicateDeclaration("profile", name, reader.getLocation());
            }

            //final Set<String> includes = new HashSet<String>();  // See commented out section below.
            //final ModelNode profileIncludes = new ModelNode();
            // Content
            // Sequence
            final Map<String, List<ModelNode>> profileOps = new LinkedHashMap<String, List<ModelNode>>();
            while (reader.nextTag() != END_ELEMENT) {
                Namespace ns = Namespace.forUri(reader.getNamespaceURI());
                switch (ns) {
                    case UNKNOWN: {
                        if (Element.forName(reader.getLocalName()) != Element.SUBSYSTEM) {
                            throw unexpectedElement(reader);
                        }
                        String namespace = reader.getNamespaceURI();
                        if (profileOps.containsKey(namespace)) {
                            throw MESSAGES.duplicateDeclaration("subsystem", name, reader.getLocation());
                        }
                        // parse content
                        final List<ModelNode> subsystems = new ArrayList<ModelNode>();
                        reader.handleAny(subsystems);

                        profileOps.put(namespace, subsystems);

                        break;
                    }
                    case DOMAIN_1_0:
                    case DOMAIN_1_1:
                    case DOMAIN_1_2:
                    case DOMAIN_1_3: {
                        requireNamespace(reader, expectedNs);
                        // include should come first
                        if (profileOps.size() > 0) {
                            throw unexpectedElement(reader);
                        }
                        if (Element.forName(reader.getLocalName()) != Element.INCLUDE) {
                            throw unexpectedElement(reader);
                        }
                        //Remove support for profile includes until 7.2.0
                        if (ns == Namespace.DOMAIN_1_0) {
                            HOST_CONTROLLER_LOGGER.warnIgnoringProfileInclude(reader.getLocation());
                        }
                        throw unexpectedElement(reader);
                        /* This will be reintroduced for 7.2.0, leave commented out
                         final String includedName = readStringAttributeElement(reader, Attribute.PROFILE.getLocalName());
                         if (! names.contains(includedName)) {
                         throw MESSAGES.profileNotFound(reader.getLocation());
                         }
                         if (! includes.add(includedName)) {
                         throw MESSAGES.duplicateProfile(reader.getLocation());
                         }
                         profileIncludes.add(includedName);
                         break;
                         */
                    }
                    default: {
                        throw unexpectedElement(reader);
                    }
                }
            }

            // Let extensions modify the profile
            Set<ProfileParsingCompletionHandler> completionHandlers = extensionRegistry.getProfileParsingCompletionHandlers();
            for (ProfileParsingCompletionHandler completionHandler : completionHandlers) {
                completionHandler.handleProfileParsingCompletion(profileOps, list);
            }

            final ModelNode profile = new ModelNode();
            profile.get(OP).set(ADD);
            profile.get(OP_ADDR).set(address).add(ModelDescriptionConstants.PROFILE, name);
            /* This will be reintroduced for 7.2.0, leave commented out
             profile.get(INCLUDES).set(profileIncludes);
             */
            list.add(profile);

            // Process subsystems
            for (List<ModelNode> subsystems : profileOps.values()) {
                for (final ModelNode update : subsystems) {
                    // Process relative subsystem path address
                    final ModelNode subsystemAddress = address.clone().set(address).add(ModelDescriptionConstants.PROFILE, name);
                    for (final Property path : update.get(OP_ADDR).asPropertyList()) {
                        subsystemAddress.add(path.getName(), path.getValue().asString());
                    }
                    update.get(OP_ADDR).set(subsystemAddress);
                    list.add(update);
                }
            }
        }
    }

    private void parseManagementClientContent(XMLExtendedStreamReader reader, ModelNode address, Namespace expectedNs, List<ModelNode> list) throws XMLStreamException {
        requireNoAttributes(reader);

        boolean rolloutPlansAdded = false;
        while (reader.nextTag() != END_ELEMENT) {
            requireNamespace(reader, expectedNs);
            Element element = Element.forName(reader.getLocalName());
            switch (element) {
                case ROLLOUT_PLANS: {
                    parseRolloutPlans(reader, address, list);
                    rolloutPlansAdded = true;
                    break;
                }
                default: {
                    throw unexpectedElement(reader);
                }
            }
        }

        if (!rolloutPlansAdded) {
            initializeRolloutPlans(address, list);
        }
    }

    private void parseRolloutPlans(XMLExtendedStreamReader reader, ModelNode address, List<ModelNode> list) throws XMLStreamException {

        String hash = readStringAttributeElement(reader, Attribute.SHA1.getLocalName());

        ModelNode addAddress = address.clone().add(MANAGEMENT_CLIENT_CONTENT, ROLLOUT_PLANS);
        ModelNode addOp = Util.getEmptyOperation(ADD, addAddress);
        try {
            addOp.get(HASH).set(HashUtil.hexStringToByteArray(hash));
        } catch (final Exception e) {
            throw MESSAGES.invalidSha1Value(e, hash, Attribute.SHA1.getLocalName(), reader.getLocation());
        }

        list.add(addOp);
    }

    private void initializeRolloutPlans(ModelNode address, List<ModelNode> list) {

        ModelNode addAddress = address.clone().add(MANAGEMENT_CLIENT_CONTENT, ROLLOUT_PLANS);
        ModelNode addOp = Util.getEmptyOperation(ADD, addAddress);
        list.add(addOp);
    }

    private void writeProfile(final XMLExtendedStreamWriter writer, final String profileName, final ModelNode profileNode, final ModelMarshallingContext context) throws XMLStreamException {

        writer.writeStartElement(Element.PROFILE.getLocalName());
        writer.writeAttribute(Attribute.NAME.getLocalName(), profileName);

        if (profileNode.hasDefined(INCLUDES)) {
            for (final ModelNode include : profileNode.get(INCLUDES).asList()) {
                writer.writeEmptyElement(INCLUDE);
                writer.writeAttribute(PROFILE, include.asString());
            }
        }
        if (profileNode.hasDefined(SUBSYSTEM)) {
            final Set<String> subsystemNames = profileNode.get(SUBSYSTEM).keys();
            if (subsystemNames.size() > 0) {
                String defaultNamespace = writer.getNamespaceContext().getNamespaceURI(XMLConstants.DEFAULT_NS_PREFIX);
                for (String subsystemName : subsystemNames) {
                    try {
                        ModelNode subsystem = profileNode.get(SUBSYSTEM, subsystemName);
                        XMLElementWriter<SubsystemMarshallingContext> subsystemWriter = context.getSubsystemWriter(subsystemName);
                        if (subsystemWriter != null) { // FIXME -- remove when extensions are doing the registration
                            subsystemWriter.writeContent(writer, new SubsystemMarshallingContext(subsystem, writer));
                        }
                    } finally {
                        writer.setDefaultNamespace(defaultNamespace);
                    }
                }
            }
        }
        writer.writeEndElement();
    }

    private void writeDomainDeployments(final XMLExtendedStreamWriter writer, final ModelNode modelNode) throws XMLStreamException {

        final Set<String> deploymentNames = modelNode.keys();
        if (deploymentNames.size() > 0) {
            writer.writeStartElement(Element.DEPLOYMENTS.getLocalName());
            for (String uniqueName : deploymentNames) {
                final ModelNode deployment = modelNode.get(uniqueName);
                writer.writeStartElement(Element.DEPLOYMENT.getLocalName());
                writeAttribute(writer, Attribute.NAME, uniqueName);
                DeploymentAttributes.RUNTIME_NAME.marshallAsAttribute(deployment, writer);
                final List<ModelNode> contentItems = deployment.require(CONTENT).asList();
                for (ModelNode contentItem : contentItems) {
                    writeContentItem(writer, contentItem);
                }
                writer.writeEndElement();
            }
            writer.writeEndElement();
        }
    }

    private void writeServerGroupDeployments(final XMLExtendedStreamWriter writer, final ModelNode modelNode) throws XMLStreamException {

        final Set<String> deploymentNames = modelNode.keys();
        if (deploymentNames.size() > 0) {
            writer.writeStartElement(Element.DEPLOYMENTS.getLocalName());
            for (String uniqueName : deploymentNames) {
                final ModelNode deployment = modelNode.get(uniqueName);
                writer.writeStartElement(Element.DEPLOYMENT.getLocalName());
                writeAttribute(writer, Attribute.NAME, uniqueName);
                DeploymentAttributes.RUNTIME_NAME.marshallAsAttribute(deployment, writer);
                DeploymentAttributes.ENABLED.marshallAsAttribute(deployment, writer);
                writer.writeEndElement();
            }
            writer.writeEndElement();
        }
    }

    private void writeServerGroup(final XMLExtendedStreamWriter writer, final String groupName, final ModelNode group) throws XMLStreamException {
        writer.writeStartElement(Element.SERVER_GROUP.getLocalName());
        writer.writeAttribute(Attribute.NAME.getLocalName(), groupName);

        ServerGroupResourceDefinition.PROFILE.marshallAsAttribute(group, writer);
        ServerGroupResourceDefinition.MANAGEMENT_SUBSYSTEM_ENDPOINT.marshallAsAttribute(group, writer);

        // JVM
        if (group.hasDefined(JVM)) {
            for (final Property jvm : group.get(JVM).asPropertyList()) {
                JvmXml.writeJVMElement(writer, jvm.getName(), jvm.getValue());
                break; // TODO just write the first !?
            }
        }

        // Socket binding ref
        if (group.hasDefined(SOCKET_BINDING_GROUP) || group.hasDefined(SOCKET_BINDING_PORT_OFFSET)) {
            writer.writeStartElement(Element.SOCKET_BINDING_GROUP.getLocalName());
            ServerGroupResourceDefinition.SOCKET_BINDING_GROUP.marshallAsAttribute(group, writer);
            ServerGroupResourceDefinition.SOCKET_BINDING_PORT_OFFSET.marshallAsAttribute(group, writer);
            writer.writeEndElement();
        }

        if (group.hasDefined(DEPLOYMENT)) {
            writeServerGroupDeployments(writer, group.get(DEPLOYMENT));
        }
        if (group.hasDefined(DEPLOYMENT_OVERLAY)) {
            writeDeploymentOverlays(writer, group.get(DEPLOYMENT_OVERLAY));
            writeNewLine(writer);
        }
        // System properties
        if (group.hasDefined(SYSTEM_PROPERTY)) {
            writeProperties(writer, group.get(SYSTEM_PROPERTY), Element.SYSTEM_PROPERTIES, false);
        }

        writer.writeEndElement();
    }

    private void writeManagementClientContent(XMLExtendedStreamWriter writer, ModelNode modelNode) throws XMLStreamException {
        boolean hasRolloutPlans = modelNode.hasDefined(ROLLOUT_PLANS) && modelNode.get(ROLLOUT_PLANS).hasDefined(HASH);
        boolean mustWrite = hasRolloutPlans; // || other elements we may add later
        if (mustWrite) {
            writer.writeStartElement(Element.MANAGEMENT_CLIENT_CONTENT.getLocalName());
            if (hasRolloutPlans) {
                writer.writeEmptyElement(Element.ROLLOUT_PLANS.getLocalName());
                writer.writeAttribute(Attribute.SHA1.getLocalName(), HashUtil.bytesToHexString(modelNode.get(ROLLOUT_PLANS).get(HASH).asBytes()));
            }
            writer.writeEndElement();
        }
    }

    private class ManagementXmlDelegate extends ManagementXml.Delegate {

        @Override
        public void parseSecurityRealms(XMLExtendedStreamReader reader, ModelNode address, Namespace expectedNs, List<ModelNode> list) throws XMLStreamException {
            // Not supported yet
            throw new UnsupportedOperationException();
        }

        @Override
        public void parseOutboundConnections(XMLExtendedStreamReader reader, ModelNode address, Namespace expectedNs, List<ModelNode> list) throws XMLStreamException {
            // Not supported yet
            throw new UnsupportedOperationException();
        }

        @Override
        public void parseAccessControl(final XMLExtendedStreamReader reader, final ModelNode address, final Namespace expectedNs,
                final List<ModelNode> list) throws XMLStreamException {
            ModelNode accAuthzAddr = address.clone().add(ACCESS, AUTHORIZATION);

            final int count = reader.getAttributeCount();
            for (int i = 0; i < count; i++) {

                final String value = reader.getAttributeValue(i);
                if (!isNoNamespaceAttribute(reader, i)) {
                    throw ParseUtils.unexpectedAttribute(reader, i);
                }

                final Attribute attribute = Attribute.forName(reader.getAttributeLocalName(i));
                if (attribute == Attribute.PROVIDER) {
                    ModelNode provider = AccessAuthorizationResourceDefinition.PROVIDER.parse(value, reader);
                    ModelNode op = Util.getWriteAttributeOperation(accAuthzAddr, AccessAuthorizationResourceDefinition.PROVIDER.getName(), provider);

                    list.add(op);
                } else {
                    throw unexpectedAttribute(reader, i);
                }
            }

            while (reader.hasNext() && reader.nextTag() != END_ELEMENT) {
                requireNamespace(reader, expectedNs);
                final Element element = Element.forName(reader.getLocalName());
                switch (element) {
                    case ROLE_MAPPING:
                        ManagementXml.parseAccessControlRoleMapping(reader, accAuthzAddr, expectedNs, list);
                        break;
                    case SERVER_GROUP_SCOPED_ROLES:
                        ManagementXml.parseServerGroupScopedRoles(reader, accAuthzAddr, expectedNs, list);
                        break;
                    case HOST_SCOPED_ROLES:
                        ManagementXml.parseHostScopedRoles(reader, accAuthzAddr, expectedNs, list);
                        break;
                    case CONSTRAINTS: {
                        ManagementXml.parseAccessControlConstraints(reader, accAuthzAddr, expectedNs, list);
                        break;
                    }
                    default: {
                        throw unexpectedElement(reader);
                    }
                }
            }
        }

    }
}
