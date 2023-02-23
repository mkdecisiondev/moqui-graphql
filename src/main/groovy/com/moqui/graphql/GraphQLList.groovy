package com.moqui.graphql


import graphql.PublicApi
import graphql.schema.GraphQLInputType
import graphql.schema.GraphQLModifiedType
import graphql.schema.GraphQLNonNull
import graphql.schema.GraphQLNullableType
import graphql.schema.GraphQLOutputType
import graphql.schema.GraphQLSchemaElement
import graphql.schema.GraphQLType
import graphql.schema.GraphQLTypeUtil
import graphql.schema.GraphQLTypeVisitor
import graphql.schema.SchemaElementChildrenContainer;
import graphql.util.TraversalControl;
import graphql.util.TraverserContext;

import java.util.Collections;
import java.util.List;
import java.util.Objects;

import static graphql.Assert.assertNotNull;

/**
 * A modified type that indicates there is a list of the underlying wrapped type, eg a list of strings or a list of booleans.
 *
 * See http://graphql.org/learn/schema/#lists-and-non-null for more details on the concept
 */
@PublicApi
public class GraphQLList implements GraphQLType, GraphQLInputType, GraphQLOutputType, GraphQLModifiedType, GraphQLNullableType {


    private final GraphQLType originalWrappedType;
    private GraphQLType replacedWrappedType;

    public static final String CHILD_WRAPPED_TYPE = "wrappedType";


    /**
     * A factory method for creating list types so that when used with static imports allows
     * more readable code such as
     * {@code .type(list(GraphQLString)) }
     *
     * @param wrappedType the type to wrap as being a list
     *
     * @return a GraphQLList of that wrapped type
     */
    public static graphql.schema.GraphQLList list(GraphQLType wrappedType) {
        return new graphql.schema.GraphQLList(wrappedType);
    }


    public GraphQLList(GraphQLType wrappedType) {
        assertNotNull(wrappedType, () -> "wrappedType can't be null");
        this.originalWrappedType = wrappedType;
    }


    @Override
    public GraphQLType getWrappedType() {
        return replacedWrappedType != null ? replacedWrappedType : originalWrappedType;
    }

    public GraphQLType getOriginalWrappedType() {
        return originalWrappedType;
    }

    void replaceType(GraphQLType type) {
        this.replacedWrappedType = type;
    }


    public boolean isEqualTo(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        graphql.schema.GraphQLList that = (graphql.schema.GraphQLList) o;
        GraphQLType wrappedType = getWrappedType();
        if (wrappedType instanceof GraphQLNonNull) {
            return ((GraphQLNonNull) wrappedType).isEqualTo(that.getWrappedType());
        }
        return Objects.equals(wrappedType, that.getWrappedType());
    }

    @Override
    public TraversalControl accept(TraverserContext<GraphQLSchemaElement> context, GraphQLTypeVisitor visitor) {
        return visitor.visitGraphQLList(this, context);
    }

    @Override
    public List<GraphQLSchemaElement> getChildren() {
        return Collections.singletonList(getWrappedType());
    }

    @Override
    public SchemaElementChildrenContainer getChildrenWithTypeReferences() {
        return SchemaElementChildrenContainer.newSchemaElementChildrenContainer()
            .child(CHILD_WRAPPED_TYPE, originalWrappedType)
            .build();
    }

    @Override
    public GraphQLSchemaElement withNewChildren(SchemaElementChildrenContainer newChildren) {
        return list(newChildren.getChildOrNull(CHILD_WRAPPED_TYPE));
    }

    @Override
    public GraphQLSchemaElement copy() {
        return new graphql.schema.GraphQLList(originalWrappedType);
    }


    @Override
    public String toString() {
        return GraphQLTypeUtil.simplePrint(this);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public final boolean equals(Object o) {
        return super.equals(o);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public final int hashCode() {
        return super.hashCode();
    }

}

