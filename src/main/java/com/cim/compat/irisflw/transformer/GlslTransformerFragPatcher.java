package com.cim.compat.irisflw.transformer;

import io.github.douira.glsl_transformer.GLSLParser;
import io.github.douira.glsl_transformer.GLSLParser.CompoundStatementContext;
import io.github.douira.glsl_transformer.ast.data.ChildNodeList;
import io.github.douira.glsl_transformer.ast.node.TranslationUnit;
import io.github.douira.glsl_transformer.ast.node.Version;
import io.github.douira.glsl_transformer.ast.node.abstract_node.ASTNode;
import io.github.douira.glsl_transformer.ast.node.declaration.Declaration;
import io.github.douira.glsl_transformer.ast.node.declaration.DeclarationMember;
import io.github.douira.glsl_transformer.ast.node.declaration.TypeAndInitDeclaration;
import io.github.douira.glsl_transformer.ast.node.expression.Expression;
import io.github.douira.glsl_transformer.ast.node.expression.ReferenceExpression;
import io.github.douira.glsl_transformer.ast.node.expression.binary.AssignmentExpression;
import io.github.douira.glsl_transformer.ast.node.expression.unary.MemberAccessExpression;
import io.github.douira.glsl_transformer.ast.node.external_declaration.DeclarationExternalDeclaration;
import io.github.douira.glsl_transformer.ast.node.external_declaration.ExternalDeclaration;
import io.github.douira.glsl_transformer.ast.node.external_declaration.FunctionDefinition;
import io.github.douira.glsl_transformer.ast.node.statement.CompoundStatement;
import io.github.douira.glsl_transformer.ast.node.type.specifier.BuiltinNumericTypeSpecifier;
import io.github.douira.glsl_transformer.ast.node.type.specifier.TypeSpecifier;
import io.github.douira.glsl_transformer.ast.query.Root;
import io.github.douira.glsl_transformer.ast.query.RootSupplier;
import io.github.douira.glsl_transformer.ast.query.index.ExternalDeclarationIndex;
import io.github.douira.glsl_transformer.ast.query.index.IdentifierIndex;
import io.github.douira.glsl_transformer.ast.query.index.SuperclassNodeIndex;
import io.github.douira.glsl_transformer.ast.query.match.AutoHintedMatcher;
import io.github.douira.glsl_transformer.ast.transform.ASTBuilder;
import io.github.douira.glsl_transformer.ast.transform.ASTInjectionPoint;
import io.github.douira.glsl_transformer.ast.transform.JobParameters;
import io.github.douira.glsl_transformer.ast.transform.SingleASTTransformer;
import io.github.douira.glsl_transformer.ast.traversal.ASTBaseVisitor;
import io.github.douira.glsl_transformer.ast.traversal.ASTVisitor;
import io.github.douira.glsl_transformer.parser.ParseShape;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import net.irisshaders.iris.helpers.StringPair;
import net.irisshaders.iris.shaderpack.parsing.CommentDirective;
import net.irisshaders.iris.shaderpack.parsing.CommentDirectiveParser;
import net.irisshaders.iris.shaderpack.parsing.CommentDirective.Type;
import net.irisshaders.iris.shaderpack.preprocessor.JcppProcessor;
import com.cim.compat.irisflw.IrisFlw;

public class GlslTransformerFragPatcher {
   static final String FLW_VERTEX_POS_DECL = "flw_vertexPos";
   static final String flw_vertexTexCoord = "flw_vertexTexCoord";
   static final String flw_vertexColor = "flw_vertexColor";
   static final String flw_vertexOverlay = "flw_vertexOverlay";
   static final String flw_vertexLight = "flw_vertexLight";
   static final String flw_vertexNormal = "flw_vertexNormal";
   private final SingleASTTransformer<GlslTransformerFragPatcher.ContextParameter> transformer = new SingleASTTransformer<GlslTransformerFragPatcher.ContextParameter>() {
      {
         this.setRootSupplier(RootSupplier.PREFIX_UNORDERED_ED_EXACT);
      }

      public TranslationUnit parseTranslationUnit(Root rootInstance, String input) {
         Matcher matcher = GlslTransformerFragPatcher.versionPattern.matcher(input);
         if (!matcher.find()) {
            throw new IllegalArgumentException("No #version directive found in source code! See debugging.md for more information.");
         } else {
            int versionNum = Integer.parseInt(matcher.group(1));
            if (versionNum < 330) {
               versionNum = 330;
               String ignored = matcher.replaceAll("#version 330");
               IrisFlw.LOGGER.warn("GLSL version is lower than 330, set to 330");
            }

            GlslTransformerFragPatcher.this.transformer.getLexer().version = Version.fromNumber(versionNum);
            return super.parseTranslationUnit(rootInstance, input);
         }
      }
   };
   private final SingleASTTransformer<GlslTransformerFragPatcher.ContextParameter> flwTransformer;
   public static final AutoHintedMatcher<Expression> glTextureMatrix0;
   public static final AutoHintedMatcher<Expression> glTextureMatrix1;
   public static final AutoHintedMatcher<Expression> glTextureMatrix2;
   public static final Set<String> toRemoveAttributesSet;
   public static final AutoHintedMatcher<Expression> ftransformExpr;
   public static final ASTVisitor<Boolean> vNormalMemberReassignMatchVisitor;
   public static final ASTVisitor<Boolean> vTexCoordsMemberReassignMatchVisitor;
   private static final ParseShape<CompoundStatementContext, CompoundStatement> CompoundStatementShape;
   private static final Pattern boxCoordDetector;
   private static final Pattern versionPattern;
   private static final boolean useLightSector = false;

   public GlslTransformerFragPatcher() {
      this.transformer.setParseLineDirectives(true);
      this.transformer.setTransformation(this::transform);
      this.flwTransformer = new SingleASTTransformer();
      this.flwTransformer.setRootSupplier(new RootSupplier(SuperclassNodeIndex::withOrdered, IdentifierIndex::withOnlyExact, ExternalDeclarationIndex::withOnlyExactOrdered));
      this.flwTransformer.getLexer().version = Version.GLSL33;
   }

   private void transform(TranslationUnit tree, Root root, GlslTransformerFragPatcher.ContextParameter parameter) {
      String vertexTemplate = parameter.flwTemplate;
      String processedFlwSource = JcppProcessor.glslPreprocessSource(vertexTemplate, List.of(new StringPair("FRAGMENT_SHADER", "1")));
      parameter.flwTree = this.flwTransformer.parseSeparateTranslationUnit(processedFlwSource);
      ChildNodeList<ExternalDeclaration> predefinesStats = this.ProcessFlywheelPredefine(tree, parameter);
      tree.injectNodes(ASTInjectionPoint.BEFORE_DECLARATIONS, predefinesStats);
   }

   private static void RemoveOriginalAttributes(Root root, Map<String, Integer> attrVectorDims) {
      root.process(root.nodeIndex.getStream(DeclarationExternalDeclaration.class).distinct(), (node) -> {
         Declaration patt7651$temp = node.getDeclaration();
         if (patt7651$temp instanceof TypeAndInitDeclaration) {
            TypeAndInitDeclaration typeAndInitDeclaration = (TypeAndInitDeclaration)patt7651$temp;
            Optional<DeclarationMember> foundMember = typeAndInitDeclaration.getMembers().stream().filter((member) -> {
               return toRemoveAttributesSet.contains(member.getName().getName());
            }).findAny();
            if (foundMember.isPresent()) {
               TypeSpecifier patt8025$temp = typeAndInitDeclaration.getType().getTypeSpecifier();
               if (patt8025$temp instanceof BuiltinNumericTypeSpecifier) {
                  BuiltinNumericTypeSpecifier numericTypeSpecifier = (BuiltinNumericTypeSpecifier)patt8025$temp;
                  String name = ((DeclarationMember)foundMember.get()).getName().getName();
                  int[] dimensions = numericTypeSpecifier.type.getDimensions();
                  int dim = dimensions.length > 0 ? dimensions[0] : 1;
                  attrVectorDims.put(name, dim);
               }

               node.detachAndDelete();
            }
         }

      });
   }

   private ChildNodeList<ExternalDeclaration> ProcessFlywheelPredefine(TranslationUnit tree, GlslTransformerFragPatcher.ContextParameter parameter) {
      TranslationUnit flwTree = parameter.flwTree;
      String flwSource = parameter.flwTemplate;
      Root flwPredefineRoot = flwTree.getRoot();
      ExternalDeclaration mainFunc = (ExternalDeclaration)flwTree.getOneMainDefinitionBody().getAncestor(ExternalDeclaration.class);
      HashSet<String> toRenameSet = new HashSet();
      flwTree.getRoot().process(flwTree.getChildren().stream().filter((x) -> {
         return x != mainFunc;
      }), (node) -> {
         if (node instanceof FunctionDefinition) {
            FunctionDefinition functionDefinition = (FunctionDefinition)node;
            String functionName = functionDefinition.getFunctionPrototype().getName().getName();
            if (!functionName.toLowerCase().startsWith("flw") && !functionName.toLowerCase().startsWith("_flw")) {
               toRenameSet.add(functionName);
            }
         } else if (node instanceof DeclarationExternalDeclaration) {
            DeclarationExternalDeclaration declarationExternalDeclaration = (DeclarationExternalDeclaration)node;
            Declaration declaration = declarationExternalDeclaration.getDeclaration();
            if (declaration instanceof TypeAndInitDeclaration) {
               TypeAndInitDeclaration typeAndInitDeclaration = (TypeAndInitDeclaration)declaration;
               TypeSpecifier typeSpecifier = typeAndInitDeclaration.getType().getTypeSpecifier();
               if (typeSpecifier instanceof BuiltinNumericTypeSpecifier) {
                  BuiltinNumericTypeSpecifier numericTypeSpecifier = (BuiltinNumericTypeSpecifier)typeSpecifier;
                  Stream<String> names = typeAndInitDeclaration.getMembers().stream().map((member) -> {
                     return member.getName().getName();
                  }).filter((name) -> {
                     return !name.toLowerCase().startsWith("flw") && !name.toLowerCase().startsWith("_flw");
                  });
                  toRenameSet.addAll((Collection)names.collect(Collectors.toSet()));
               }
            }
         }

      });
      toRenameSet.forEach((name) -> {
         String newName = "flw_" + name;
         flwTree.getRoot().rename(name, newName);
      });
      return ChildNodeList.collect(flwTree.getChildren().stream().filter((x) -> {
         return x != mainFunc;
      }), flwTree);
   }

   private void replaceReferenceExpressionsWithCorrectSwizzle(Root root, SingleASTTransformer<GlslTransformerFragPatcher.ContextParameter> transformer, String name, String expression, int dimension) {
      root.process(root.identifierIndex.getStream(name), (identifier) -> {
         ASTNode parent = identifier.getParent();
         if (parent instanceof ReferenceExpression) {
            ReferenceExpression referenceExpression = (ReferenceExpression)parent;
            if (referenceExpression.getParent() instanceof MemberAccessExpression) {
               parent.replaceByAndDelete(transformer.parseExpression(identifier.getRoot(), expression));
            } else {
               parent.replaceByAndDelete(transformer.parseExpression(identifier.getRoot(), this.getSwizzleFromDimension(expression, dimension)));
            }

         }
      });
   }

   private String getSwizzleFromDimension(String identifierName, int dimension) {
      String var10001;
      switch(dimension) {
      case 1:
         var10001 = ".x";
         break;
      case 2:
         var10001 = ".xy";
         break;
      case 3:
         var10001 = ".xyz";
         break;
      case 4:
         var10001 = ".xyzw";
         break;
      default:
         var10001 = "";
      }

      return identifierName + var10001;
   }

   private String getZeroFromDimension(int dimension) {
      String var10000;
      switch(dimension) {
      case 1:
         var10000 = "0.0";
         break;
      case 2:
         var10000 = "vec2(0.0)";
         break;
      case 3:
         var10000 = "vec3(0.0)";
         break;
      case 4:
         var10000 = "vec4(0.0)";
         break;
      default:
         var10000 = "";
      }

      return var10000;
   }

   public String patch(String irisSource, String flwSource) {
      Optional<CommentDirective> drawBufferDirectives = CommentDirectiveParser.findDirective(irisSource, Type.DRAWBUFFERS);
      Optional<CommentDirective> renderTargetDirectives = CommentDirectiveParser.findDirective(irisSource, Type.RENDERTARGETS);
      String transformed = (String)this.transformer.transform(irisSource, new GlslTransformerFragPatcher.ContextParameter(flwSource));
      StringBuffer sb = new StringBuffer();
      drawBufferDirectives.ifPresent((directive) -> {
         sb.append("/* DRAWBUFFERS:");
         sb.append(directive.getDirective());
         sb.append(" */\n");
      });
      renderTargetDirectives.ifPresent((directive) -> {
         sb.append("/* RENDERTARGETS:");
         sb.append(directive.getDirective());
         sb.append(" */\n");
      });
      sb.append(transformed);
      return sb.toString();
   }

   static {
      glTextureMatrix0 = new AutoHintedMatcher("gl_TextureMatrix[0]", ParseShape.EXPRESSION);
      glTextureMatrix1 = new AutoHintedMatcher("gl_TextureMatrix[1]", ParseShape.EXPRESSION);
      glTextureMatrix2 = new AutoHintedMatcher("gl_TextureMatrix[2]", ParseShape.EXPRESSION);
      toRemoveAttributesSet = Set.of("at_tangent", "at_midBlock", "mc_Entity", "mc_midTexCoord");
      ftransformExpr = new AutoHintedMatcher("ftransform()", ParseShape.EXPRESSION);
      vNormalMemberReassignMatchVisitor = new GlslTransformerFragPatcher.ReassignMatcherVisitor("v", "normal");
      vTexCoordsMemberReassignMatchVisitor = new GlslTransformerFragPatcher.ReassignMatcherVisitor("v", "texCoords");
       CompoundStatementShape = new ParseShape<>(
               CompoundStatementContext.class,
               (GLSLParser parser) -> parser.compoundStatement(),
               (ASTBuilder builder, CompoundStatementContext ctx) -> builder.visitCompoundStatement(ctx)
       );
      boxCoordDetector = Pattern.compile("BoxCoord");
      versionPattern = Pattern.compile("^.*#version\\s+(\\d+)", 32);
   }

   public static class ContextParameter implements JobParameters {
      public boolean hasBoxCoord;
      public String flwTemplate;
      public TranslationUnit flwTree;

      public ContextParameter(String flwVertexSource) {
         this.flwTemplate = flwVertexSource;
      }
   }

   private static class ReassignMatcherVisitor extends ASTBaseVisitor<Boolean> {
      private final String targetName;
      private final String targetMember;
      private boolean isTargetMemberAccess = false;

      public ReassignMatcherVisitor(String targetName, String targetMember) {
         this.targetName = targetName;
         this.targetMember = targetMember;
      }

      private boolean isVNormalMemberAccess(ASTNode node) {
         if (!(node instanceof MemberAccessExpression)) {
            return false;
         } else {
            MemberAccessExpression memberAccessExpression = (MemberAccessExpression)node;
            boolean var10000;
            if (memberAccessExpression.getMember().getName().equals(this.targetMember)) {
               Expression var4 = memberAccessExpression.getOperand();
               if (var4 instanceof ReferenceExpression) {
                  ReferenceExpression referenceExpression = (ReferenceExpression)var4;
                  if (referenceExpression.getIdentifier().getName().equals(this.targetName)) {
                     var10000 = true;
                     return var10000;
                  }
               }
            }

            var10000 = false;
            return var10000;
         }
      }

      public Boolean startVisit(ASTNode node) {
         this.isTargetMemberAccess = false;
         return (Boolean)super.startVisit(node);
      }

      public Boolean visitRaw(ASTNode node) {
         if (node instanceof AssignmentExpression) {
            AssignmentExpression assignmentExpression = (AssignmentExpression)node;
            Expression left = assignmentExpression.getLeft();
            if (left instanceof MemberAccessExpression) {
               MemberAccessExpression memberAccessExpression = (MemberAccessExpression)left;
               boolean leftIsVNormal = this.isVNormalMemberAccess(memberAccessExpression);
               if (leftIsVNormal) {
                  Expression right = assignmentExpression.getRight();
                  this.isTargetMemberAccess = true;
                  return (Boolean)right.accept(this);
               }
            }
         } else if (this.isTargetMemberAccess && node instanceof MemberAccessExpression) {
            MemberAccessExpression memberAccessExpression = (MemberAccessExpression)node;
            if (this.isVNormalMemberAccess(memberAccessExpression)) {
               return true;
            }
         }

         return (Boolean)node.accept(this);
      }

      public Boolean defaultResult() {
         return false;
      }

      public Boolean aggregateResult(Boolean aggregate, Boolean nextResult) {
         return aggregate || nextResult;
      }
   }
}
