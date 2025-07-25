/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright (c) 2014 ForgeRock AS. All Rights Reserved
 *
 * The contents of this file are subject to the terms
 * of the Common Development and Distribution License
 * (the License). You may not use this file except in
 * compliance with the License.
 *
 * You can obtain a copy of the License at
 * http://forgerock.org/license/CDDLv1.0.html
 * See the License for the specific language governing
 * permission and limitations under the License.
 *
 * When distributing Covered Code, include this CDDL
 * Header Notice in each file and include the License file
 * at http://forgerock.org/license/CDDLv1.0.html
 * If applicable, add the following below the CDDL Header,
 * with the fields enclosed by brackets [] replaced by
 * your own identifying information:
 * "Portions Copyrighted [year] [name of copyright owner]"
 */
package com.evolveum.polygon.connector.googleapps;

import com.google.api.services.directory.Directory;
import com.google.api.services.directory.model.*;
import com.google.api.services.directory.model.Schema;
import com.google.common.base.CharMatcher;
import com.google.common.escape.Escaper;
import com.google.common.escape.Escapers;
import org.identityconnectors.common.CollectionUtil;
import org.identityconnectors.common.StringUtil;
import org.identityconnectors.common.logging.Log;
import org.identityconnectors.common.security.GuardedString;
import org.identityconnectors.common.security.SecurityUtil;
import org.identityconnectors.framework.common.exceptions.ConnectorException;
import org.identityconnectors.framework.common.exceptions.InvalidAttributeValueException;
import org.identityconnectors.framework.common.objects.*;
import org.identityconnectors.framework.common.objects.filter.*;
import org.identityconnectors.framework.common.objects.AttributeInfo.Flags;

import java.io.IOException;
import java.util.*;

import static com.evolveum.polygon.connector.googleapps.GoogleAppsConnector.ID_ATTR;
import static com.evolveum.polygon.connector.googleapps.GoogleAppsConnector.PHOTO_ATTR;

/**
 *
 * @author Laszlo Hordos
 */
public class UserHandler implements FilterVisitor<StringBuilder, Directory.Users.List> {

    /**
     * Setup logging for the {@link UserHandler}.
     */
    private static final Log logger = Log.getLog(UserHandler.class);

    public static final String ID_ETAG = "id,etag";
    public static final String NAME = "name";
    public static final String ADMIN_CREATED_ATTR = "adminCreated";
    public static final String NON_EDITABLE_ALIASES_ATTR = "nonEditableAliases";
    public static final String DIRECT_MEMBERS_COUNT_ATTR = "directMembersCount";
    public static final String MY_CUSTOMER_ID = "my_customer";
    public static final String SUSPENDED_ATTR = "suspended";
    public static final String CHANGE_PASSWORD_AT_NEXT_LOGIN_ATTR = "changePasswordAtNextLogin";
    public static final String IP_WHITELISTED_ATTR = "ipWhitelisted";
    public static final String ORG_UNIT_PATH_ATTR = "orgUnitPath";
    public static final String INCLUDE_IN_GLOBAL_ADDRESS_LIST_ATTR = "includeInGlobalAddressList";
    public static final String IMS_ATTR = "ims";
    public static final String EMAILS_ATTR = "emails";
    public static final String EXTERNAL_IDS_ATTR = "externalIds";
    public static final String RELATIONS_ATTR = "relations";
    public static final String ADDRESSES_ATTR = "addresses";
    public static final String ORGANIZATIONS_ATTR = "organizations";
    public static final String PHONES_ATTR = "phones";
    public static final String GIVEN_NAME_ATTR = "givenName";
    public static final String FAMILY_NAME_ATTR = "familyName";
    public static final String FULL_NAME_ATTR = "fullName";
    public static final String IS_ADMIN_ATTR = "isAdmin";
    public static final String IS_DELEGATED_ADMIN_ATTR = "isDelegatedAdmin";
    public static final String LAST_LOGIN_TIME_ATTR = "lastLoginTime";
    public static final String CREATION_TIME_ATTR = "creationTime";
    public static final String AGREED_TO_TERMS_ATTR = "agreedToTerms";
    public static final String SUSPENSION_REASON_ATTR = "suspensionReason";
    public static final String ALIASES_ATTR = "aliases";
    public static final String CUSTOMER_ID_ATTR = "customerId";
    public static final String IS_MAILBOX_SETUP_ATTR = "isMailboxSetup";
    public static final String THUMBNAIL_PHOTO_URL_ATTR = "thumbnailPhotoUrl";
    public static final String DELETION_TIME_ATTR = "deletionTime";
    public static final String LOCATIONS_ATTR = "locations";
    public static final String OFFICE = "Office";

    private static final Map<String, String> NAME_DICTIONARY;
    private static final Set<String> S;
    private static final Set<String> SW;

    static {
        Map<String, String> dictionary = CollectionUtil.newCaseInsensitiveMap();
        dictionary.put(NAME, NAME);
        dictionary.put("email", "email");
        dictionary.put(Name.NAME, "email");
        dictionary.put(GIVEN_NAME_ATTR, GIVEN_NAME_ATTR);
        dictionary.put(FAMILY_NAME_ATTR, FAMILY_NAME_ATTR);
        dictionary.put(IS_ADMIN_ATTR, IS_ADMIN_ATTR);
        dictionary.put(IS_DELEGATED_ADMIN_ATTR, IS_DELEGATED_ADMIN_ATTR);
        dictionary.put("isSuspended", "isSuspended");
        dictionary.put("im", "im");
        dictionary.put("externalId", "externalId");
        dictionary.put("manager", "manager");
        dictionary.put("managerId", "managerId");
        dictionary.put("directManager", "directManager");
        dictionary.put("directManagerId", "directManagerId");
        dictionary.put("address", "address");
        dictionary.put("addressPoBox", "addressPoBox");
        dictionary.put("address/poBox", "addressPoBox");
        dictionary.put("addressExtended", "addressExtended");
        dictionary.put("address/extended", "addressExtended");
        dictionary.put("addressStreet", "addressStreet");
        dictionary.put("address/street", "addressStreet");
        dictionary.put("addressLocality", "addressLocality");
        dictionary.put("address/locality", "addressLocality");
        dictionary.put("addressRegion", "addressRegion");
        dictionary.put("address/region", "addressRegion");
        dictionary.put("addressPostalCode", "addressPostalCode");
        dictionary.put("address/postalCode", "addressPostalCode");
        dictionary.put("addressCountry", "addressCountry");
        dictionary.put("address/country", "addressCountry");
        dictionary.put("orgName", "orgName");
        dictionary.put("organizations/name", "orgName");
        NAME_DICTIONARY = dictionary;

        Set<String> s = CollectionUtil.newCaseInsensitiveSet();
        s.add(NAME);
        s.add("email");
        s.add("givenName");
        s.add("familyName");
        SW = CollectionUtil.newCaseInsensitiveSet();
        SW.addAll(s);

        s.add("im");
        s.add("externalId");
        s.add("address");
        s.add("addressPoBox");
        s.add("addressExtended");
        s.add("addressStreet");
        s.add("addressLocality");
        s.add("addressRegion");
        s.add("addressPostalCode");
        s.add("addressCountry");
        s.add("orgName");
        s.add("orgTitle");
        s.add("orgDepartment");
        s.add("orgDescription");
        s.add("orgCostCenter");
        S = s;
    }

    private static final Escaper STRING_ESCAPER = Escapers.builder().addEscape('\'', "\\'").build();

    public StringBuilder visitAndFilter(Directory.Users.List list, AndFilter andFilter) {
        StringBuilder builder = new StringBuilder();
        boolean first = true;
        for (Filter filter : andFilter.getFilters()) {
            if (filter == null) {
                continue;
            }
            StringBuilder sb = filter.accept(this, list);
            if (null != sb) {
                if (!first) {
                    builder.append(' ');
                } else {
                    first = false;
                }
                builder.append(sb);
            }
        }
        return builder;
    }

    public StringBuilder visitContainsFilter(Directory.Users.List list, ContainsFilter filter) {
        String filedName = NAME_DICTIONARY.get(filter.getName());
        if (null != filedName && S.contains(filedName)) {
            return getStringBuilder(filter.getAttribute(), ':', null, filedName);
        } else {
            // Warning: not supported field name
            throw new InvalidAttributeValueException("");
        }
    }

    /**
     * Surround with single quotes ' if the query contains whitespace. Escape
     * single quotes in queries with \', for example 'Valentine\'s Day'.
     *
     * @param attribute
     * @param operator
     * @param postfix
     * @param filedName
     * @return
     */
    protected StringBuilder getStringBuilder(Attribute attribute, char operator, Character postfix,
            String filedName) {
        StringBuilder builder = new StringBuilder();
        builder.append(filedName).append(operator);
        String stringValue = AttributeUtil.getAsStringValue(attribute);
        if (StringUtil.isNotBlank(stringValue)) {
            stringValue = STRING_ESCAPER.escape(stringValue);
            if (CharMatcher.whitespace().matchesAnyOf(stringValue)) {
                builder.append('\'').append(stringValue);
                if (null != postfix) {
                    builder.append(postfix);
                }
                builder.append('\'');
            } else {
                builder.append(stringValue);
                if (null != postfix) {
                    builder.append(postfix);
                }
            }
        }
        return builder;
    }

    public StringBuilder visitStartsWithFilter(Directory.Users.List list, StartsWithFilter filter) {
        String filedName = NAME_DICTIONARY.get(filter.getName());
        if (null != filedName && SW.contains(filedName)) {
            return getStringBuilder(filter.getAttribute(), ':', '*', filedName);
        } else {
            // Warning: not supported field name
            throw new InvalidAttributeValueException("");
        }
    }

    public StringBuilder visitEqualsFilter(Directory.Users.List list, EqualsFilter equalsFilter) {
        if (AttributeUtil.namesEqual(equalsFilter.getName(), "customer")) {
            if (null != list.getDomain()) {
                throw new InvalidAttributeValueException(
                        "The 'customer' and 'domain' can not be in the same query");
            } else {
                list.setCustomer(AttributeUtil.getStringValue(equalsFilter.getAttribute()));
            }
        } else if (AttributeUtil.namesEqual(equalsFilter.getName(), "domain")) {
            if (null != list.getCustomer()) {
                throw new InvalidAttributeValueException(
                        "The 'customer' and 'domain' can not be in the same query");
            } else {
                list.setDomain(AttributeUtil.getStringValue(equalsFilter.getAttribute()));
            }
        } else {
            String filedName = NAME_DICTIONARY.get(equalsFilter.getName());
            if (null != filedName) {
                return getStringBuilder(equalsFilter.getAttribute(), '=', null, filedName);
            } else {
                // Warning: not supported field name
                throw new InvalidAttributeValueException("");
            }
        }

        return null;
    }

    public StringBuilder visitContainsAllValuesFilter(Directory.Users.List list,
            ContainsAllValuesFilter filter) {
        return null;
    }

    public StringBuilder visitExtendedFilter(Directory.Users.List list, Filter filter) {
        return null;
    }

    public StringBuilder visitGreaterThanFilter(Directory.Users.List list, GreaterThanFilter filter) {
        return null;
    }

    public StringBuilder visitGreaterThanOrEqualFilter(Directory.Users.List list,
            GreaterThanOrEqualFilter filter) {
        return null;
    }

    public StringBuilder visitLessThanFilter(Directory.Users.List list, LessThanFilter filter) {
        return null;
    }

    public StringBuilder visitLessThanOrEqualFilter(Directory.Users.List list,
            LessThanOrEqualFilter filter) {
        return null;
    }

    public StringBuilder visitNotFilter(Directory.Users.List list, NotFilter filter) {
        return null;
    }

    public StringBuilder visitOrFilter(Directory.Users.List list, OrFilter filter) {
        return null;
    }

    public StringBuilder visitEndsWithFilter(Directory.Users.List list, EndsWithFilter filter) {
        return null;
    }

    @Override
    public StringBuilder visitEqualsIgnoreCaseFilter(Directory.Users.List list, EqualsIgnoreCaseFilter filter)
    {
        return null;
    }

    // /////////////
    //
    // USER https://developers.google.com/admin-sdk/directory/v1/reference/users
    //
    // /////////////
    public static ObjectClassInfo getUserClassInfo(Schemas schemas, GoogleAppsConfiguration googleAppsConfiguration) {
        // @formatter:off
            /*
         {
         "kind": "admin#directory#user",
         "id": string,
         "etag": etag,
         "primaryEmail": string,
         "name": {
         "givenName": string,
         "familyName": string,
         "fullName": string
         },
         "isAdmin": boolean,
         "isDelegatedAdmin": boolean,
         "lastLoginTime": datetime,
         "creationTime": datetime,
         "deletionTime": datetime,
         "agreedToTerms": boolean,
         "password": string,
         "hashFunction": string,
         "suspended": boolean,
         "suspensionReason": string,
         "changePasswordAtNextLogin": boolean,
         "ipWhitelisted": boolean,
         "ims": [
         {
         "type": string,
         "customType": string,
         "protocol": string,
         "customProtocol": string,
         "im": string,
         "primary": boolean
         }
         ],
         "emails": [
         {
         "address": string,
         "type": string,
         "customType": string,
         "primary": boolean
         }
         ],
         "externalIds": [
         {
         "value": string,
         "type": string,
         "customType": string
         }
         ],
         "relations": [
         {
         "value": string,
         "type": string,
         "customType": string
         }
         ],
         "addresses": [
         {
         "type": string,
         "customType": string,
         "sourceIsStructured": boolean,
         "formatted": string,
         "poBox": string,
         "extendedAddress": string,
         "streetAddress": string,
         "locality": string,
         "region": string,
         "postalCode": string,
         "country": string,
         "primary": boolean,
         "countryCode": string
         }
         ],
         "organizations": [
         {
         "name": string,
         "title": string,
         "primary": boolean,
         "type": string,
         "customType": string,
         "department": string,
         "symbol": string,
         "location": string,
         "description": string,
         "domain": string,
         "costCenter": string
         }
         ],
         "phones": [
         {
         "value": string,
         "primary": boolean,
         "type": string,
         "customType": string
         }
         ],
         "aliases": [
         string
         ],
         "nonEditableAliases": [
         string
         ],
         "customerId": string,
         "orgUnitPath": string,
         "isMailboxSetup": boolean,
         "includeInGlobalAddressList": boolean,
         "thumbnailPhotoUrl": string
         }
         */
        // @formatter:on
        ObjectClassInfoBuilder builder = new ObjectClassInfoBuilder();

        // primaryEmail
        builder.addAttributeInfo(Name.INFO);

        builder.addAttributeInfo(AttributeInfoBuilder.define(GIVEN_NAME_ATTR).setRequired(true)
                .build());
        builder.addAttributeInfo(AttributeInfoBuilder.define(FAMILY_NAME_ATTR).setRequired(true)
                .build());
        builder.addAttributeInfo(AttributeInfoBuilder.define(FULL_NAME_ATTR).setUpdateable(false)
                .setCreateable(false).build());

        // Virtual attribute Modify supported
        builder.addAttributeInfo(AttributeInfoBuilder.build(IS_ADMIN_ATTR, Boolean.TYPE));

        builder.addAttributeInfo(AttributeInfoBuilder.define(IS_DELEGATED_ADMIN_ATTR, Boolean.TYPE)
                .setUpdateable(false).setCreateable(false).build());

        builder.addAttributeInfo(AttributeInfoBuilder.define(LAST_LOGIN_TIME_ATTR).setUpdateable(
                false).setCreateable(false).setMultiValued(true).build());
        builder.addAttributeInfo(AttributeInfoBuilder.define(CREATION_TIME_ATTR).setUpdateable(
                false).setCreateable(false).setMultiValued(true).build());
        builder.addAttributeInfo(AttributeInfoBuilder.define(AGREED_TO_TERMS_ATTR, Boolean.TYPE)
                .setUpdateable(false).setCreateable(false).build());

        builder.addAttributeInfo(AttributeInfoBuilder.define(OperationalAttributes.PASSWORD_NAME,
                GuardedString.class).setRequired(true).setReadable(false).setReturnedByDefault(
                        false).build());

        builder.addAttributeInfo(AttributeInfoBuilder.build(SUSPENDED_ATTR, Boolean.class));
        builder.addAttributeInfo(AttributeInfoBuilder.define(SUSPENSION_REASON_ATTR).setUpdateable(
                false).setCreateable(false).build());
        builder.addAttributeInfo(AttributeInfoBuilder.build(CHANGE_PASSWORD_AT_NEXT_LOGIN_ATTR,
                Boolean.class));
        builder.addAttributeInfo(AttributeInfoBuilder.build(IP_WHITELISTED_ATTR, Boolean.class));

        builder.addAttributeInfo(AttributeInfoBuilder.define(IMS_ATTR).setMultiValued(
                true).build());
        builder.addAttributeInfo(AttributeInfoBuilder.define(EMAILS_ATTR)
                .setMultiValued(true).build());
        builder.addAttributeInfo(AttributeInfoBuilder.define(EXTERNAL_IDS_ATTR)
                .setMultiValued(true).build());
        builder.addAttributeInfo(AttributeInfoBuilder.define(RELATIONS_ATTR)
                .setMultiValued(true).build());
        builder.addAttributeInfo(AttributeInfoBuilder.define(ADDRESSES_ATTR)
                .setMultiValued(true).build());
        builder.addAttributeInfo(AttributeInfoBuilder.define(ORGANIZATIONS_ATTR)
                .setMultiValued(true).build());
        builder.addAttributeInfo(AttributeInfoBuilder.define(PHONES_ATTR)
                .setMultiValued(true).build());
        builder.addAttributeInfo(AttributeInfoBuilder.define(ALIASES_ATTR)
                .setMultiValued(true).build());
        builder.addAttributeInfo(AttributeInfoBuilder.define(LOCATIONS_ATTR)
                .setMultiValued(true).build());

        builder.addAttributeInfo(AttributeInfoBuilder.define(NON_EDITABLE_ALIASES_ATTR)
                .setUpdateable(false).setCreateable(false).setMultiValued(true).build());

        builder.addAttributeInfo(AttributeInfoBuilder.define(CUSTOMER_ID_ATTR).setUpdateable(false)
                .setCreateable(false).build());

        builder.addAttributeInfo(AttributeInfoBuilder.build(ORG_UNIT_PATH_ATTR));

        builder.addAttributeInfo(AttributeInfoBuilder.define(IS_MAILBOX_SETUP_ATTR, Boolean.class)
                .setUpdateable(false).setCreateable(false).build());

        builder.addAttributeInfo(AttributeInfoBuilder.build(INCLUDE_IN_GLOBAL_ADDRESS_LIST_ATTR,
                Boolean.class));

        builder.addAttributeInfo(AttributeInfoBuilder.define(THUMBNAIL_PHOTO_URL_ATTR)
                .setUpdateable(false).setCreateable(false).build());
        builder.addAttributeInfo(AttributeInfoBuilder.define(DELETION_TIME_ATTR).setUpdateable(
                false).setCreateable(false).build());

        // Virtual Attribute
        builder.addAttributeInfo(AttributeInfoBuilder.define(PHOTO_ATTR, byte[].class)
                .setReturnedByDefault(false).build());



        if (schemas != null){
            List<ArrayList<CustomAttribute>> allSchemas = getCustomSchemaAttributes(schemas,googleAppsConfiguration);
            if (!allSchemas.isEmpty()){
                for (ArrayList<CustomAttribute> schema : allSchemas){
                    for (CustomAttribute attribute : schema){
                        if (attribute.getType().equals("BOOL")){
                            builder.addAttributeInfo(AttributeInfoBuilder.define(attribute.getName()).setType(Boolean.class).setMultiValued(attribute.isMultiValued())
                                    .build());
                        } else {
                            builder.addAttributeInfo(AttributeInfoBuilder.define(attribute.getName())
                                    .build());
                        }

                    }
                }
            }
        }


        /* 
        builder.addAttributeInfo(PredefinedAttributeInfos.GROUPS.setReturnedByDefault(true));
        AttributeInfoBuilder subjectId = new AttributeInfoBuilder();
        subjectId.setName("subjectId");
        subjectId.setNativeName("subject-id");
        subjectId.setCreateable(true);
        subjectId.setUpdateable(true);
        subjectId.setReadable(true);
        subjectId.setRequired(false);
        subjectId.setMultiValued(false);
        attributes.add(subjectId.build());
        builder.addAttributeInfo(AttributeInfoBuilder.define("groups").setNativeName("__GROUPS__").setMultiValued(true).setReturnedByDefault(true).build());
        */
        AttributeInfo GROUPS = AttributeInfoBuilder.build( PredefinedAttributes.GROUPS_NAME, String.class, EnumSet.of(Flags.MULTIVALUED));
        builder.addAttributeInfo(GROUPS);

        return builder.build();
    }

    public static List<ArrayList<CustomAttribute>> getCustomSchemaAttributes(Schemas schemas,GoogleAppsConfiguration googleAppsConfiguration){

        ArrayList<ArrayList<CustomAttribute>> customSchemas = new ArrayList<>();
        List<Schema> schemaList = schemas.getSchemas();

        for (Schema schema : schemaList) {
            ArrayList<CustomAttribute> customAttributes = new ArrayList<>();
            String schemaName = schema.getSchemaName();
            if (!approveScheme(schemaName, googleAppsConfiguration)) {
                continue;
            }
            for (SchemaFieldSpec fieldSpec : schema.getFields()) {
                customAttributes.add(new CustomAttribute(schemaName+"."+ fieldSpec.getFieldName(),fieldSpec.getFieldType(),fieldSpec.getMultiValued(),schemaName,fieldSpec.getFieldName()));
            }
            customSchemas.add(customAttributes);
        }

        return customSchemas;
    }

    public static class CustomAttribute {
        private String name;
        private String type;
        private String schemaName;
        private String fieldName;
        private boolean multiValued;

        public CustomAttribute(String name, String type, boolean multiValued,String schemaName, String fieldName) {
            this.name = name;
            this.type = type;
            this.multiValued = multiValued;
            this.schemaName = schemaName;
            this.fieldName = fieldName;
        }

        // Getters
        public String getName() {
            return name;
        }

        public String getType() {
            return type;
        }
        public String getSchemaName() {
            return schemaName;
        }
        public String getFieldName() {
            return fieldName;
        }

        public boolean isMultiValued() {
            return multiValued;
        }
    }

    public static boolean approveScheme(String schemaName,GoogleAppsConfiguration googleAppsConfiguration){
        if (googleAppsConfiguration.getProjection().equals("FULL")){
            return true;
        }
        if (!googleAppsConfiguration.getProjection().equals("BASIC")){
            String[] parts = googleAppsConfiguration.getCustomFieldMask().split(",");

            for (String part : parts) {
                if (schemaName.equals(part)){
                    return true;
                }
            }
            return false;
        }

        return false;
    }

    // https://support.google.com/a/answer/33386
    public static Directory.Users.Insert createUser(Directory.Users users,
            AttributesAccessor attributes,Schemas schemas,GoogleAppsConfiguration configuration) {
        User user = new User();
        user.setPrimaryEmail(GoogleAppsUtil.getName(attributes.getName()));
        GuardedString password = attributes.getPassword();
        if (null != password) {
            user.setPassword(SecurityUtil.decrypt(password));
        } else {
            throw new InvalidAttributeValueException("Missing required attribute '__PASSWORD__'");
        }

        user.setName(new UserName());
        // givenName The user's first name. Required when creating a user
        // account.
        String givenName = attributes.findString(GIVEN_NAME_ATTR);
        if (StringUtil.isNotBlank(givenName)) {
            user.getName().setGivenName(givenName);
        } else {
            throw new InvalidAttributeValueException(
                    "Missing required attribute 'givenName'. The user's first name. Required when creating a user account.");
        }

        // familyName The user's last name. Required when creating a user
        // account.
        String familyName = attributes.findString(FAMILY_NAME_ATTR);
        if (StringUtil.isNotBlank(familyName)) {
            user.getName().setFamilyName(familyName);
        } else {
            throw new InvalidAttributeValueException(
                    "Missing required attribute 'familyName'. The user's last name. Required when creating a user account.");
        }

        // Optional
        user.setIms(GoogleAppsUtil.getStructAttr((Attribute) attributes.find(IMS_ATTR)));
        user.setEmails(GoogleAppsUtil.getStructAttr((Attribute) attributes.find(EMAILS_ATTR)));
        user.setExternalIds(GoogleAppsUtil.getStructAttr((Attribute) attributes.find(EXTERNAL_IDS_ATTR)));
        user.setRelations(GoogleAppsUtil.getStructAttr((Attribute) attributes.find(RELATIONS_ATTR)));
        user.setAddresses(GoogleAppsUtil.getStructAttr((Attribute) attributes.find(ADDRESSES_ATTR)));
        user.setOrganizations(GoogleAppsUtil.getStructAttr((Attribute) attributes.find(ORGANIZATIONS_ATTR)));
        user.setPhones(GoogleAppsUtil.getStructAttr((Attribute) attributes.find(PHONES_ATTR)));
        user.setLocations(GoogleAppsUtil.getStructAttr((Attribute) attributes.find(LOCATIONS_ATTR)));

        user.setSuspended(attributes.findBoolean(SUSPENDED_ATTR));
        user.setChangePasswordAtNextLogin(attributes
                .findBoolean(CHANGE_PASSWORD_AT_NEXT_LOGIN_ATTR));
        user.setIpWhitelisted(attributes.findBoolean(IP_WHITELISTED_ATTR));
        user.setOrgUnitPath(attributes.findString(ORG_UNIT_PATH_ATTR));
        user.setIncludeInGlobalAddressList(attributes
                .findBoolean(INCLUDE_IN_GLOBAL_ADDRESS_LIST_ATTR));

        if (schemas != null){
            Map<String, Map<String, Object>> customSchemas = new HashMap<>();
            List<ArrayList<CustomAttribute>> allSchemas = getCustomSchemaAttributes(schemas,configuration);
            if (!allSchemas.isEmpty()){
                for (ArrayList<CustomAttribute> customAttributes : allSchemas){
                    String schemaName = null;
                    Map<String, Object> customAttr = new HashMap<>();
                    for (CustomAttribute attribute : customAttributes){
                        Attribute schemaAttr = attributes.find(attribute.getName());
                        if (schemaAttr != null) {
                            schemaName = attribute.schemaName;
                            if (!attribute.multiValued){
                                if (schemaAttr.getValue()== null){
                                    customAttr.put(attribute.fieldName,null);
                                } else {
                                    customAttr.put(attribute.fieldName,schemaAttr.getValue().get(0));
                                }
                            } else {
                                customAttr.put(attribute.fieldName,schemaAttr.getValue().toString());
                            }

                        }
                    }
                    if (!customAttr.isEmpty()){
                        customSchemas.put(schemaName,customAttr);
                    }
                }
                if (!customSchemas.isEmpty()){
                    user.setCustomSchemas(customSchemas);
                }
            }
        }

        try {
            return users.insert(user).setFields(ID_ETAG);
            // } catch (HttpResponseException e){
        } catch (IOException e) {
            logger.warn(e, "Failed to initialize Groups#Insert");
            throw ConnectorException.wrap(e);
        }
    }

    public static Directory.Users.Patch updateUser(Directory.Users users, Uid uid,
            AttributesAccessor attributes,Schemas schemas,GoogleAppsConfiguration configuration) {
        User content = null;

        Name email = attributes.getName();
        if (email != null && email.getNameValue() != null) {
            content = new User();
            content.setPrimaryEmail(email.getNameValue());
        }

        Attribute givenName = attributes.find(GIVEN_NAME_ATTR);
        if (null != givenName) {
            String stringValue = GoogleAppsUtil.getStringValueWithDefault(givenName, null);
            if (null != stringValue) {
                if (null == content) {
                    content = new User();
                }
                content.setName(new UserName());
                content.getName().setGivenName(stringValue);
            }
        }

        Attribute familyName = attributes.find(FAMILY_NAME_ATTR);
        if (null != familyName) {
            String stringValue = GoogleAppsUtil.getStringValueWithDefault(familyName, null);
            if (null != stringValue) {
                if (null == content) {
                    content = new User();
                }
                if (null == content.getName()) {
                    content.setName(new UserName());
                }
                content.getName().setFamilyName(stringValue);
            }
        }

        GuardedString password = attributes.getPassword();
        if (null != password) {
            if (null == content) {
                content = new User();
            }
            content.setPassword(SecurityUtil.decrypt(password));
        }

        Attribute suspended = attributes.find(SUSPENDED_ATTR);
        if (null != suspended) {
            Boolean booleanValue = GoogleAppsUtil.getBooleanValueWithDefault(suspended, null);
            if (null != booleanValue) {
                if (null == content) {
                    content = new User();
                }
                content.setSuspended(booleanValue);
            }
        }

        Attribute changePasswordAtNextLogin = attributes.find(CHANGE_PASSWORD_AT_NEXT_LOGIN_ATTR);
        if (null != changePasswordAtNextLogin) {
            Boolean booleanValue
                    = GoogleAppsUtil.getBooleanValueWithDefault(changePasswordAtNextLogin, null);
            if (null != booleanValue) {
                if (null == content) {
                    content = new User();
                }
                content.setChangePasswordAtNextLogin(booleanValue);
            }
        }

        Attribute ipWhitelisted = attributes.find(IP_WHITELISTED_ATTR);
        if (null != ipWhitelisted) {
            Boolean booleanValue = GoogleAppsUtil.getBooleanValueWithDefault(ipWhitelisted, null);
            if (null != booleanValue) {
                if (null == content) {
                    content = new User();
                }
                content.setIpWhitelisted(booleanValue);
            }
        }

        // Maps
        Attribute ims = attributes.find(IMS_ATTR);
        if (null != ims) {
            if (null == content) {
                content = new User();
            }
            content.setIms(GoogleAppsUtil.getStructAttr(ims));
        }

        Attribute emails = attributes.find(EMAILS_ATTR);
        if (null != emails) {
            if (null == content) {
                content = new User();
            }
            content.setEmails(GoogleAppsUtil.getStructAttr(emails));
        }

        Attribute externalIds = attributes.find(EXTERNAL_IDS_ATTR);
        if (null != externalIds) {
            if (null == content) {
                content = new User();
            }
            content.setExternalIds(GoogleAppsUtil.getStructAttr(externalIds));
        }


        Attribute relations = attributes.find(RELATIONS_ATTR);
        if (null != relations) {
            if (null == content) {
                content = new User();
            }
            content.setRelations(GoogleAppsUtil.getStructAttr(relations));
        }

        Attribute addresses = attributes.find(ADDRESSES_ATTR);
        if (null != addresses) {
            if (null == content) {
                content = new User();
            }
            content.setAddresses(GoogleAppsUtil.getStructAttr(addresses));
        }

        Attribute organizations = attributes.find(ORGANIZATIONS_ATTR);
        if (null != organizations) {
            if (null == content) {
                content = new User();
            }
            content.setOrganizations(GoogleAppsUtil.getStructAttr(organizations));
        }

        Attribute phones = attributes.find(PHONES_ATTR);
        if (null != phones) {
            if (null == content) {
                content = new User();
            }
            content.setPhones(GoogleAppsUtil.getStructAttr(phones));
        }

        Attribute locations = attributes.find(LOCATIONS_ATTR);
        if (null != locations) {
            if (null == content) {
                content = new User();
            }
            content.setLocations(GoogleAppsUtil.getStructAttr(locations));
        }

        Attribute orgUnitPath = attributes.find(ORG_UNIT_PATH_ATTR);
        if (null != orgUnitPath) {
            String stringValue = GoogleAppsUtil.getStringValueWithDefault(orgUnitPath, null);
            if (null != stringValue) {
                if (null == content) {
                    content = new User();
                }
                content.setOrgUnitPath(stringValue);
            }
        }

        Attribute includeInGlobalAddressList = attributes.find(INCLUDE_IN_GLOBAL_ADDRESS_LIST_ATTR);
        if (null != includeInGlobalAddressList) {
            Boolean booleanValue
                    = GoogleAppsUtil.getBooleanValueWithDefault(includeInGlobalAddressList, null);
            if (null != booleanValue) {
                if (null == content) {
                    content = new User();
                }
                content.setIncludeInGlobalAddressList(booleanValue);
            }
        }

        if (schemas != null){
            Map<String, Map<String, Object>> customSchemas = new HashMap<>();
            List<ArrayList<CustomAttribute>> allSchemas = getCustomSchemaAttributes(schemas,configuration);
            if (!allSchemas.isEmpty()){
                for (ArrayList<CustomAttribute> customAttributes : allSchemas){
                    String schemaName = null;
                    Map<String, Object> customAttr = new HashMap<>();
                    for (CustomAttribute attribute : customAttributes){
                        Attribute schemaAttr = attributes.find(attribute.getName());
                        if (schemaAttr != null) {
                            if (null == content) {
                                content = new User();
                            }
                            schemaName = attribute.schemaName;
                            if (!attribute.multiValued){
                                if (schemaAttr.getValue()== null){
                                    customAttr.put(attribute.fieldName,null);
                                } else {
                                    customAttr.put(attribute.fieldName,schemaAttr.getValue().get(0));
                                }
                            } else {
                                customAttr.put(attribute.fieldName,schemaAttr.getValue().toString());
                            }

                        }
                    }
                    if (!customAttr.isEmpty()){
                        customSchemas.put(schemaName,customAttr);
                    }
                }
                if (!customSchemas.isEmpty()){
                    content.setCustomSchemas(customSchemas);
                }
            }
        }

        if (null == content) {
            return null;
        }
        try {
            return users.patch(uid.getUidValue(), content).setFields(ID_ETAG);
            // } catch (HttpResponseException e){
        } catch (IOException e) {
            logger.warn(e, "Failed to initialize Groups#Patch");
            throw ConnectorException.wrap(e);
        }
    }

    public static Directory.Users.Photos.Update createUpdateUserPhoto(
            Directory.Users.Photos service, String userKey, byte[] data) {
        UserPhoto content = new UserPhoto();
        // Required
        content.setPhotoData(com.google.api.client.util.Base64.encodeBase64URLSafeString(data));

        // @formatter:off
        /*
         content.setPhotoData(com.google.api.client.util.Base64
         .encodeBase64URLSafeString((byte[]) data.get("photoData")));
         content.setHeight((Integer) data.get("height"));
         content.setWidth((Integer) data.get("width"));

         // Allowed values are JPEG, PNG, GIF, BMP, TIFF,
         content.setMimeType((String) data.get("mimeType"));
         */
        // @formatter:on
        try {
            /*Directory.Users.Photos.Update update = service.update(userKey, content).setFields(ID_ATTR);*/
            Directory.Users.Photos.Update update = service.update(userKey, content);
            return update;
            // } catch (HttpResponseException e){
        } catch (IOException e) {
            logger.warn(e, "Failed to initialize Aliases#Insert");
            throw ConnectorException.wrap(e);
        }
    }

    public static Directory.Users.Aliases.Insert createUserAlias(Directory.Users.Aliases service,
            String userKey, String alias) {
        Alias content = new Alias();
        content.setAlias(alias);
        try {
            return service.insert(userKey, content).setFields(ID_ETAG);
            // } catch (HttpResponseException e){
        } catch (IOException e) {
            logger.warn(e, "Failed to initialize Aliases#Insert");
            throw ConnectorException.wrap(e);
        }
    }

    public static Directory.Users.Aliases.Delete deleteUserAlias(Directory.Users.Aliases service,
            String userKey, String alias) {
        try {
            return service.delete(userKey, alias);
            // } catch (HttpResponseException e){
        } catch (IOException e) {
            logger.warn(e, "Failed to initialize Aliases#Delete");
            throw ConnectorException.wrap(e);
        }
    }

}
