/*
 * This software is in the public domain under CC0 1.0 Universal plus a
 * Grant of Patent License.
 *
 * To the extent possible under law, the author(s) have dedicated all
 * copyright and related and neighboring rights to this software to the
 * public domain worldwide. This software is distributed without any
 * warranty.
 *
 * You should have received a copy of the CC0 Public Domain Dedication
 * along with this software (see the LICENSE.md file). If not, see
 * <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com.moqui.graphql

import graphql.GraphQLException
import graphql.language.FloatValue
import graphql.language.IntValue
import graphql.language.StringValue
import graphql.scalar.GraphqlIntCoercing
import graphql.schema.Coercing
import graphql.schema.CoercingParseValueException
import graphql.schema.CoercingSerializeException
import graphql.schema.GraphQLScalarType
import groovy.transform.CompileStatic

import java.sql.Timestamp

@CompileStatic
class Scalars {
    private static final BigInteger LONG_MAX = BigInteger.valueOf(Long.MAX_VALUE)
    private static final BigInteger LONG_MIN = BigInteger.valueOf(Long.MIN_VALUE)
    private static final BigInteger INT_MAX = BigInteger.valueOf(Integer.MAX_VALUE);
    private static final BigInteger INT_MIN = BigInteger.valueOf(Integer.MIN_VALUE);
    private static final BigInteger BYTE_MAX = BigInteger.valueOf(Byte.MAX_VALUE);
    private static final BigInteger BYTE_MIN = BigInteger.valueOf(Byte.MIN_VALUE);
    private static final BigInteger SHORT_MAX = BigInteger.valueOf(Short.MAX_VALUE);
    private static final BigInteger SHORT_MIN = BigInteger.valueOf(Short.MIN_VALUE);

    private static boolean isNumberIsh(Object input) {
        return input instanceof Number || input instanceof String;
    }

    public static final GraphQLScalarType GraphQLTimestamp = GraphQLScalarType.newScalar()
        .name("Timestamp").description("Timestamp Type").coercing(new Coercing() {
        @Override
        Object serialize(Object input) {
            if (input instanceof String) {
                if (input == "" || input == null) return null
                return Timestamp.valueOf(input).getTime()
            } else if (input instanceof Long) {
                return new Timestamp(input).getTime()
            } else if (input instanceof Timestamp) {
                return input.getTime()
            }
            return null
        }

        @Override
        Object parseValue(Object input) {
            if (input instanceof String) {
                return Timestamp.valueOf(input)
            } else if (input instanceof Long) {
                return new Timestamp(input)
            } else if (input instanceof Timestamp) {
                return input
            }
            return null
        }

        @Override
        Object parseLiteral(Object input) {
            if (input instanceof StringValue) {
                return Timestamp.valueOf(((StringValue) input).getValue())
            } else if (input instanceof IntValue) {
                BigInteger value = ((IntValue) input).getValue()
                // Check if out of bounds.
                if (value.compareTo(LONG_MIN) < 0 || value.compareTo(LONG_MAX) > 0) {
                    throw new GraphQLException("Int literal is too big or too small for a long, would cause overflow");
                }
                return new Timestamp(value.longValue())
            }
            return null
        }
    }).build()

    public static final GraphQLScalarType GraphQLChar = GraphQLScalarType.newScalar()
        .name("Char").description("Built-in Char").coercing( new Coercing<Character, Character>() {

        private Character convertImpl(Object input) {
            if (input instanceof String && ((String) input).length() == 1) {
                return ((String) input).charAt(0);
            } else if (input instanceof Character) {
                return (Character) input;
            } else {
                return null;
            }

        }

        @Override
        public Character serialize(Object input) {
            Character result = convertImpl(input);
            if (result == null) {
                throw new CoercingSerializeException("Invalid input '" + input + "' for Char");
            }
            return result;
        }

        @Override
        public Character parseValue(Object input) {
            Character result = convertImpl(input);
            if (result == null) {
                throw new CoercingParseValueException("Invalid input '" + input + "' for Char");
            }
            return result;
        }

        @Override
        public Character parseLiteral(Object input) {
            if (!(input instanceof StringValue)) return null;
            String value = ((StringValue) input).getValue();
            if (value.length() != 1) return null;
            return value.charAt(0);
        }
    }).build();

    public static final GraphQLScalarType GraphQLBigInteger = GraphQLScalarType.newScalar()
        .name("BigInteger").description("Built-in java.math.BigInteger").coercing( new Coercing<BigInteger, BigInteger>() {

        private BigInteger convertImpl(Object input) {
            if (isNumberIsh(input)) {
                BigDecimal value;
                try {
                    value = new BigDecimal(input.toString());
                } catch (NumberFormatException e) {
                    return null;
                }
                try {
                    return value.toBigIntegerExact();
                } catch (ArithmeticException e) {
                    return null;
                }
            }
            return null;

        }

        @Override
        public BigInteger serialize(Object input) {
            BigInteger result = convertImpl(input);
            if (result == null) {
                throw new CoercingSerializeException("Invalid input '" + input + "' for BigInteger");
            }
            return result;
        }

        @Override
        public BigInteger parseValue(Object input) {
            BigInteger result = convertImpl(input);
            if (result == null) {
                throw new CoercingParseValueException("Invalid input '" + input + "' for BigInteger");
            }
            return result;
        }

        @Override
        public BigInteger parseLiteral(Object input) {
            if (input instanceof StringValue) {
                try {
                    return new BigDecimal(((StringValue) input).getValue()).toBigIntegerExact();
                } catch (NumberFormatException | ArithmeticException e) {
                    return null;
                }
            } else if (input instanceof IntValue) {
                return ((IntValue) input).getValue();
            } else if (input instanceof FloatValue) {
                try {
                    return ((FloatValue) input).getValue().toBigIntegerExact();
                } catch (ArithmeticException e) {
                    return null;
                }
            }
            return null;
        }
    }).build();

    public static final GraphQLScalarType GraphQLBigDecimal = GraphQLScalarType.newScalar()
        .name("BigDecimal").description("Built-in java.math.BigDecimal").coercing( new Coercing<BigDecimal, BigDecimal>() {

        private BigDecimal convertImpl(Object input) {
            if (isNumberIsh(input)) {
                try {
                    return new BigDecimal(input.toString());
                } catch (NumberFormatException e) {
                    return null;
                }
            }
            return null;

        }

        @Override
        public BigDecimal serialize(Object input) {
            BigDecimal result = convertImpl(input);
            if (result == null) {
                throw new CoercingSerializeException("Invalid input '" + input + "' for BigDecimal");
            }
            return result;
        }

        @Override
        public BigDecimal parseValue(Object input) {
            BigDecimal result = convertImpl(input);
            if (result == null) {
                throw new CoercingParseValueException("Invalid input '" + input + "' for BigDecimal");
            }
            return result;
        }

        @Override
        public BigDecimal parseLiteral(Object input) {
            if (input instanceof StringValue) {
                try {
                    return new BigDecimal(((StringValue) input).getValue());
                } catch (NumberFormatException e) {
                    return null;
                }
            } else if (input instanceof IntValue) {
                return new BigDecimal(((IntValue) input).getValue());
            } else if (input instanceof FloatValue) {
                return ((FloatValue) input).getValue();
            }
            return null;
        }
    }).build();

    public static final GraphQLScalarType GraphQLLong = GraphQLScalarType.newScalar()
        .name("Long").description("Built-in Long").coercing( new Coercing<Long, Long>() {
        private Long convertImpl(Object input) {
            if (input instanceof Long) {
                return (Long) input;
            } else if (isNumberIsh(input)) {
                BigDecimal value;
                try {
                    value = new BigDecimal(input.toString());
                } catch (NumberFormatException e) {
                    return null;
                }
                try {
                    return value.longValueExact();
                } catch (ArithmeticException e) {
                    return null;
                }
            } else {
                return null;
            }

        }

        @Override
        public Long serialize(Object input) {
            Long result = convertImpl(input);
            if (result == null) {
                throw new CoercingSerializeException("Invalid input '" + input + "' for Long");
            }
            return result;
        }

        @Override
        public Long parseValue(Object input) {
            Long result = convertImpl(input);
            if (result == null) {
                throw new CoercingParseValueException("Invalid input '" + input + "' for Long");
            }
            return result;
        }

        @Override
        public Long parseLiteral(Object input) {
            if (input instanceof StringValue) {
                try {
                    return Long.parseLong(((StringValue) input).getValue());
                } catch (NumberFormatException e) {
                    return null;
                }
            } else if (input instanceof IntValue) {
                BigInteger value = ((IntValue) input).getValue();
                if (value.compareTo(LONG_MIN) < 0 || value.compareTo(LONG_MAX) > 0) {
                    return null;
                }
                return value.longValue();
            }
            return null;
        }
    }).build();

    public static final GraphQLScalarType GraphQLShort = GraphQLScalarType.newScalar()
        .name("Short").description("Built-in Short as Int").coercing( new Coercing<Short, Short>() {

        private Short convertImpl(Object input) {
            if (input instanceof Short) {
                return (Short) input;
            } else if (isNumberIsh(input)) {
                BigDecimal value;
                try {
                    value = new BigDecimal(input.toString());
                } catch (NumberFormatException e) {
                    return null;
                }
                try {
                    return value.shortValueExact();
                } catch (ArithmeticException e) {
                    return null;
                }
            } else {
                return null;
            }

        }

        @Override
        public Short serialize(Object input) {
            Short result = convertImpl(input);
            if (result == null) {
                throw new CoercingSerializeException("Invalid input '" + input + "' for Short");
            }
            return result;
        }

        @Override
        public Short parseValue(Object input) {
            Short result = convertImpl(input);
            if (result == null) {
                throw new CoercingParseValueException("Invalid input '" + input + "' for Short");
            }
            return result;
        }

        @Override
        public Short parseLiteral(Object input) {
            if (!(input instanceof IntValue)) return null;
            BigInteger value = ((IntValue) input).getValue();
            if (value.compareTo(SHORT_MIN) < 0 || value.compareTo(SHORT_MAX) > 0) {
                return null;
            }
            return value.shortValue();
        }
    }).build();

    public static final GraphQLScalarType GraphQLByte = GraphQLScalarType.newScalar()
        .name("Byte").description("Built-in Byte as Int").coercing( new Coercing<Byte, Byte>() {
        private Byte convertImpl(Object input) {
            if (input instanceof Byte) {
                return (Byte) input;
            } else if (isNumberIsh(input)) {
                BigDecimal value;
                try {
                    value = new BigDecimal(input.toString());
                } catch (NumberFormatException e) {
                    return null;
                }
                try {
                    return value.byteValueExact();
                } catch (ArithmeticException e) {
                    return null;
                }
            } else {
                return null;
            }

        }

        @Override
        public Byte serialize(Object input) {
            Byte result = convertImpl(input);
            if (result == null) {
                throw new CoercingSerializeException("Invalid input '" + input + "' for Byte");
            }
            return result;
        }

        @Override
        public Byte parseValue(Object input) {
            Byte result = convertImpl(input);
            if (result == null) {
                throw new CoercingParseValueException("Invalid input '" + input + "' for Byte");
            }
            return result;
        }

        @Override
        public Byte parseLiteral(Object input) {
            if (!(input instanceof IntValue)) return null;
            BigInteger value = ((IntValue) input).getValue();
            if (value.compareTo(BYTE_MIN) < 0 || value.compareTo(BYTE_MAX) > 0) {
                return null;
            }
            return value.byteValue();
        }
    }).build();

    public static final GraphQLScalarType GraphQLString = GraphQLScalarType.newScalar()
        .name("String").description("Built-in String").coercing( new Coercing<String, String>() {
        @Override
        public String serialize(Object input) {
            return input.toString();
        }

        @Override
        public String parseValue(Object input) {
            return serialize(input);
        }

        @Override
        public String parseLiteral(Object input) {
            if (!(input instanceof StringValue)) return null;
            return ((StringValue) input).getValue();
        }
    }).build();
}
