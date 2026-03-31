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
import io.github.douira.glsl_transformer.ast.node.statement.CompoundStatement;
import io.github.douira.glsl_transformer.ast.node.statement.Statement;
import io.github.douira.glsl_transformer.ast.node.statement.terminal.ExpressionStatement;
import io.github.douira.glsl_transformer.ast.node.type.specifier.BuiltinNumericTypeSpecifier;
import io.github.douira.glsl_transformer.ast.node.type.specifier.TypeSpecifier;
import io.github.douira.glsl_transformer.ast.print.ASTPrinter;
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
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.irisshaders.iris.helpers.StringPair;
import net.irisshaders.iris.shaderpack.preprocessor.JcppProcessor;
import com.cim.compat.irisflw.IrisFlw;
import com.cim.compat.irisflw.flywheel.RenderLayerEventStateManager;

public class GlslTransformerVertPatcher {
   static final String FLW_VERTEX_POS_DECL = "flw_vertexPos";
   static final String flw_vertexTexCoord = "flw_vertexTexCoord";
   static final String flw_vertexColor = "flw_vertexColor";
   static final String flw_vertexOverlay = "flw_vertexOverlay";
   static final String flw_vertexLight = "flw_vertexLight";
   static final String flw_vertexNormal = "flw_vertexNormal";
   private final SingleASTTransformer<GlslTransformerVertPatcher.ContextParameter> transformer = new SingleASTTransformer<GlslTransformerVertPatcher.ContextParameter>() {
      {
         this.setRootSupplier(RootSupplier.PREFIX_UNORDERED_ED_EXACT);
      }

      public TranslationUnit parseTranslationUnit(Root rootInstance, String input) {
         Matcher matcher = GlslTransformerVertPatcher.versionPattern.matcher(input);
         if (!matcher.find()) {
            throw new IllegalArgumentException("No #version directive found in source code! See debugging.md for more information.");
         } else {
            int versionNum = Integer.parseInt(matcher.group(1));
            if (versionNum < 330) {
               versionNum = 330;
               String ignored = matcher.replaceAll("#version 330");
               IrisFlw.LOGGER.warn("GLSL version is lower than 330, set to 330");
            }

            GlslTransformerVertPatcher.this.transformer.getLexer().version = Version.fromNumber(versionNum);
            return super.parseTranslationUnit(rootInstance, input);
         }
      }
   };
   private final SingleASTTransformer<GlslTransformerVertPatcher.ContextParameter> flwTransformer;
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

   public GlslTransformerVertPatcher() {
      this.transformer.setTransformation(this::transform);
      this.flwTransformer = new SingleASTTransformer();
      this.flwTransformer.setRootSupplier(new RootSupplier(SuperclassNodeIndex::withOrdered, IdentifierIndex::withOnlyExact, ExternalDeclarationIndex::withOnlyExactOrdered));
      this.flwTransformer.getLexer().version = Version.GLSL33;
   }

   private void transform(TranslationUnit tree, Root root, GlslTransformerVertPatcher.ContextParameter parameter) {
      String vertexTemplate = parameter.flwVertexTemplate;
      String processedFlwSource = JcppProcessor.glslPreprocessSource(vertexTemplate, List.of(new StringPair("VERTEX_SHADER", "1")));
      parameter.flwTree = this.flwTransformer.parseSeparateTranslationUnit(processedFlwSource);
      ChildNodeList<ExternalDeclaration> predefinesStats = this.ProcessFlywheelPredefine(tree, parameter);
      ChildNodeList<Statement> prependMainStats = this.ProcessFlywheelCreateVertex(tree, parameter);
      tree.injectNodes(ASTInjectionPoint.BEFORE_DECLARATIONS, predefinesStats);
      tree.prependMainFunctionBody(prependMainStats);
      root.replaceReferenceExpressions(this.transformer, "gl_Vertex", String.format("inverse(gl_ProjectionMatrix*gl_ModelViewMatrix)* flw_viewProjection * %s", "flw_vertexPos"));
      root.replaceReferenceExpressions(this.transformer, "gl_MultiTexCoord0", String.format("vec4(%s,0,1)", "flw_vertexTexCoord"));
      root.replaceReferenceExpressions(this.transformer, "gl_Normal", "flw_vertexNormal");
      if (parameter.getUseLightLut()) {
         root.replaceReferenceExpressions(this.transformer, "gl_Color", String.format("%s * vec4(_flw_ao, _flw_ao, _flw_ao, 1)", "flw_vertexColor"));
      } else {
         root.replaceReferenceExpressions(this.transformer, "gl_Color", "flw_vertexColor");
      }

      root.replaceExpressionMatches(this.transformer, ftransformExpr, String.format("flw_viewProjection * %s", "flw_vertexPos"));
      Map<String, Integer> originalAttrVecDims = new HashMap();
      RemoveOriginalAttributes(root, originalAttrVecDims);
      Integer atTangentDim = (Integer)originalAttrVecDims.getOrDefault("at_tangent", 4);
      Integer atMidBlockDim = (Integer)originalAttrVecDims.getOrDefault("at_midBlock", 4);
      Integer mcMidTexCoordDim = (Integer)originalAttrVecDims.getOrDefault("mc_midTexCoord", 4);
      Integer mcEntityDim = (Integer)originalAttrVecDims.getOrDefault("mc_Entity", 2);
      if (IrisFlw.isUsingExtendedVertexFormat()) {
         this.replaceReferenceExpressionsWithCorrectSwizzle(root, this.transformer, "at_tangent", "_flw_at_tangent", atTangentDim);
         this.replaceReferenceExpressionsWithCorrectSwizzle(root, this.transformer, "mc_Entity", "_flw_v_mc_Entity", atTangentDim);
         this.replaceReferenceExpressionsWithCorrectSwizzle(root, this.transformer, "mc_midTexCoord", "_flw_mc_midTexCoord", mcMidTexCoordDim);
         this.replaceReferenceExpressionsWithCorrectSwizzle(root, this.transformer, "at_midBlock", "_flw_at_midBlock", atMidBlockDim);
      } else {
         root.replaceReferenceExpressions(this.transformer, "at_tangent", this.getSwizzleFromDimension("_flw_fake_tangent", atTangentDim));
         root.replaceReferenceExpressions(this.transformer, "mc_Entity", this.getZeroFromDimension(mcEntityDim));
         root.replaceReferenceExpressions(this.transformer, "mc_midTexCoord", this.getZeroFromDimension(mcMidTexCoordDim));
         root.replaceReferenceExpressions(this.transformer, "at_midBlock", this.getZeroFromDimension(atMidBlockDim));
      }

      if (!RenderLayerEventStateManager.isRenderingShadow()) {
         root.replaceReferenceExpressionsReport(this.transformer, "gl_MultiTexCoord1", String.format("(vec4(%s*256.0,0,1))", "flw_vertexLight"));
      }

   }

   private static void RemoveOriginalAttributes(Root root, Map<String, Integer> attrVectorDims) {
      root.process(root.nodeIndex.getStream(DeclarationExternalDeclaration.class).distinct(), (node) -> {
         Declaration patt9998$temp = node.getDeclaration();
         if (patt9998$temp instanceof TypeAndInitDeclaration) {
            TypeAndInitDeclaration typeAndInitDeclaration = (TypeAndInitDeclaration)patt9998$temp;
            Optional<DeclarationMember> foundMember = typeAndInitDeclaration.getMembers().stream().filter((member) -> {
               return toRemoveAttributesSet.contains(member.getName().getName());
            }).findAny();
            if (foundMember.isPresent()) {
               TypeSpecifier patt10372$temp = typeAndInitDeclaration.getType().getTypeSpecifier();
               if (patt10372$temp instanceof BuiltinNumericTypeSpecifier) {
                  BuiltinNumericTypeSpecifier numericTypeSpecifier = (BuiltinNumericTypeSpecifier)patt10372$temp;
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

   private ChildNodeList<ExternalDeclaration> ProcessFlywheelPredefine(TranslationUnit tree, GlslTransformerVertPatcher.ContextParameter parameter) {
      StringBuilder beforeDeclarationContent = new StringBuilder();
      if (!IrisFlw.isUsingExtendedVertexFormat()) {
         beforeDeclarationContent.append("vec4 _flw_fake_tangent;");
      }

      if (parameter.getUseLightLut()) {
         beforeDeclarationContent.append("float _flw_ao;");
      }

      TranslationUnit flwTree = parameter.flwTree;
      String flwSource = parameter.flwVertexTemplate;
      if (!beforeDeclarationContent.isEmpty()) {
         ExternalDeclaration additionDeclarations = (ExternalDeclaration)this.flwTransformer.parseNodeSeparate(this.flwTransformer.getRootSupplier(), ParseShape.EXTERNAL_DECLARATION, beforeDeclarationContent.toString());
         flwTree.injectNode(ASTInjectionPoint.BEFORE_DECLARATIONS, additionDeclarations);
      }

      Root flwPredefineRoot = flwTree.getRoot();
      flwPredefineRoot.process(flwTree.getRoot().nodeIndex.getStream(ExpressionStatement.class), (statement) -> {
         if ((Boolean)vNormalMemberReassignMatchVisitor.startVisit(statement)) {
            String assignStatStr = ASTPrinter.printSimple(statement);
            assignStatStr = assignStatStr.replace("v.normal", "_flw_at_tangent.xyz");
            CompoundStatement statements = (CompoundStatement)statement.getAncestor(CompoundStatement.class);
            int index = statements.getStatements().indexOf(statement);
            statements.getStatements().add(index + 1, this.flwTransformer.parseStatement(flwTree.getRoot(), assignStatStr));
         }

      });
      flwPredefineRoot.process(flwTree.getRoot().nodeIndex.getStream(ExpressionStatement.class), (statement) -> {
         if ((Boolean)vTexCoordsMemberReassignMatchVisitor.startVisit(statement)) {
            String assignStatStr = ASTPrinter.printSimple(statement);
            assignStatStr = assignStatStr.replace("v.texCoords", "_flw_mc_midTexCoord.xy");
            CompoundStatement statements = (CompoundStatement)statement.getAncestor(CompoundStatement.class);
            int index = statements.getStatements().indexOf(statement);
            statements.getStatements().add(index + 1, this.flwTransformer.parseStatement(flwTree.getRoot(), assignStatStr));
         }

      });
      ExternalDeclaration mainFunc = (ExternalDeclaration)flwTree.getOneMainDefinitionBody().getAncestor(ExternalDeclaration.class);
      return ChildNodeList.collect(flwTree.getChildren().stream().filter((x) -> {
         return x != mainFunc;
      }), flwTree);
   }

   private ChildNodeList<Statement> ProcessFlywheelCreateVertex(TranslationUnit irisTree, GlslTransformerVertPatcher.ContextParameter context) {
      StringBuilder createVertexBuilder = new StringBuilder();
      createVertexBuilder.append("{\n");
      TranslationUnit flwTree = context.flwTree;
      String flwSource = context.flwVertexTemplate;
      CompoundStatement flwMainBody = flwTree.getOneMainDefinitionBody();
      createVertexBuilder.append(ASTPrinter.printSimple(flwMainBody));
      if (!IrisFlw.isUsingExtendedVertexFormat()) {
         createVertexBuilder.append(String.format("vec3 skewedNormal = %s+vec3(0.5,0.5,0.5);\n_flw_fake_tangent = vec4(normalize(skewedNormal - %s*dot(skewedNormal, %s)).xyz,1.0);\n", "flw_vertexNormal", "flw_vertexNormal", "flw_vertexNormal"));
      }

      if (context.getUseLightLut()) {
         createVertexBuilder.append("FlwLightAo _flw_light;\nflw_light(flw_vertexPos.xyz, flw_vertexNormal, _flw_light);\nflw_vertexLight = _flw_light.light;\n_flw_ao = _flw_light.ao;\n");
      }

      createVertexBuilder.append("\n}");
      CompoundStatement compoundStatement = (CompoundStatement)this.flwTransformer.parseNodeSeparate(this.flwTransformer.getRootSupplier(), CompoundStatementShape, createVertexBuilder.toString());
      return compoundStatement.getStatements();
   }

   private void replaceReferenceExpressionsWithCorrectSwizzle(Root root, SingleASTTransformer<GlslTransformerVertPatcher.ContextParameter> transformer, String name, String expression, int dimension) {
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

   public String patch(String irisSource, String flwSource, boolean isShadow, boolean isEmbedded, boolean isExtendedVertexFormat) {
      return (String)this.transformer.transform(irisSource, new GlslTransformerVertPatcher.ContextParameter(flwSource, isShadow, isEmbedded, isExtendedVertexFormat));
   }

   static {
      glTextureMatrix0 = new AutoHintedMatcher("gl_TextureMatrix[0]", ParseShape.EXPRESSION);
      glTextureMatrix1 = new AutoHintedMatcher("gl_TextureMatrix[1]", ParseShape.EXPRESSION);
      glTextureMatrix2 = new AutoHintedMatcher("gl_TextureMatrix[2]", ParseShape.EXPRESSION);
      toRemoveAttributesSet = Set.of("at_tangent", "at_midBlock", "mc_Entity", "mc_midTexCoord");
      ftransformExpr = new AutoHintedMatcher("ftransform()", ParseShape.EXPRESSION);
      vNormalMemberReassignMatchVisitor = new GlslTransformerVertPatcher.ReassignMatcherVisitor("v", "normal");
      vTexCoordsMemberReassignMatchVisitor = new GlslTransformerVertPatcher.ReassignMatcherVisitor("v", "texCoords");
       CompoundStatementShape = new ParseShape<>(
               CompoundStatementContext.class,
               (GLSLParser parser) -> parser.compoundStatement(),
               (ASTBuilder builder, CompoundStatementContext ctx) -> builder.visitCompoundStatement(ctx)
       );
      boxCoordDetector = Pattern.compile("BoxCoord");
      versionPattern = Pattern.compile("^.*#version\\s+(\\d+)", 32);
   }

   public static class ContextParameter implements JobParameters {
      public boolean isShadow;
      public boolean isEmbedded;
      public boolean isExtendedVertexFormat;
      public String flwVertexTemplate;
      public TranslationUnit flwTree;

      public boolean getUseLightLut() {
         return !this.isShadow && this.isEmbedded;
      }

      public ContextParameter(String flwVertexTemplate, boolean isShadow, boolean isEmbedded, boolean isExtendedVertexFormat) {
         this.flwVertexTemplate = flwVertexTemplate;
         this.isShadow = isShadow;
         this.isEmbedded = isEmbedded;
         this.isExtendedVertexFormat = isExtendedVertexFormat;
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
