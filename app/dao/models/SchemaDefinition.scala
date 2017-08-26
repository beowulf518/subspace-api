package dao.models

import java.sql.Timestamp

import org.eclipse.jgit.diff.DiffEntry.ChangeType
import sangria.macros.derive._
import sangria.relay._
import sangria.schema.{Field, _}
import utils.{AsyncUtils, RandomGenerator, FirebaseUtil}
import sangria.relay.Mutation
import org.eclipse.jgit.lib.ObjectId
import sangria.validation.{StringCoercionViolation, ValueCoercionViolation}

import scala.util.{Failure, Success, Try}
import org.eclipse.jgit.lib.Constants
import org.eclipse.jgit.revwalk.{RevBlob, RevCommit}
import sangria.ast
import sangria.macros.derive
import play.api.libs.json._

object SchemaDefinition {
  val NodeDefinition(nodeInterface, nodeField, nodesField) =
    Node.definition((globalId: GlobalId, ctx: Context[GraphQLContext, Unit]) ⇒ {
      if (globalId.typeName == "Viewer") {
        Some(Viewer())
      } else if (globalId.typeName == "Ref") {
        ctx.ctx.jGitUtil.getRefById(globalId.id)
      } else if (globalId.typeName == "User") {
        ctx.ctx.allDAO.userDAO.getById(globalId.id)
      } else if (globalId.typeName == "Repository") {
        ctx.ctx.repoDAO.find(globalId.id)
      } else if (globalId.typeName == "Stash") {
        ctx.ctx.allDAO.stashDAO.getById(globalId.id)
      } else {
        None
      }
    }, Node.possibleNodeTypes[GraphQLContext, Node](ViewerType, UserType, RepositoryType, ProjectType, TopicType, RefType, CommitType)
  )

//  def idFields[T: Identifiable] = fields[Unit, T](
//    Node.globalIdField,
//    Field("rawId", StringType, resolve = ctx ⇒ implicitly[Identifiable[T]].id(ctx.value)))

//  def addIdFields[T: Identifiable] = AddFields[Unit, T](
//    Node.globalIdField,
//    Field("rawId", StringType, resolve = ctx ⇒ implicitly[Identifiable[T]].id(ctx.value)))

  case object GitObjectIDCoercionViolation extends ValueCoercionViolation("GitObjectID value expected")

  def parseObjectId(s: String) = Try(ObjectId.fromString(s)) match {
    case Success(objectId) ⇒ Right(objectId)
    case Failure(_) ⇒ Left(GitObjectIDCoercionViolation)
  }

  val IdArg = Argument("id", OptionInputType(StringType))
  val NameArg = Argument("name", OptionInputType(StringType))
  val FirebaseIdArg = Argument("firebaseId", OptionInputType(StringType))
  val UserIdOptArg = Argument("userId", OptionInputType(StringType))
  val OwnerArg = Argument("ownerId", OptionInputType(StringType))
  val OwnerName = Argument("ownerName", OptionInputType(StringType))
  val RepositoryIdArg = Argument("repositoryId", OptionInputType(StringType))
  val CommitId = Argument("commitId", StringType)
  val ProjectIdArg = Argument("projectId", OptionInputType(StringType))
  val ProviderIdArg = Argument("providerId", OptionInputType(StringType))
  val ProviderArg = Argument("provider", StringType)
  val ProviderOptArg = Argument("provider", OptionInputType(StringType))
  val LoginArg = Argument(
    "login", OptionInputType(StringType),
    description = "Filter by userName"
  )
  val RefPrefix = Argument("refPrefix", OptionInputType(StringType))
  val RefName = Argument(
    "refName", StringType,
    description = "Filter by branch / stash name"
  )
  val IsOnlineArg = Argument("isOnline", OptionInputType(BooleanType))
  val PathOption = Argument(
    "path", OptionInputType(StringType),
    description = "If non-null, filters to only show touching files under this path."
  )
  val isStash = Argument(
    "isStash", OptionInputType(BooleanType),
    description = "If non-null, filter only stash type of ref or not (branch type). If null, default filter to branch type."
  )
  val DiagramIdArg = Argument(
    "diagramId", OptionInputType(StringType),
    description = "Filter by diagramId"
  )
  val SortByArg = Argument(
    "sortBy", OptionInputType(StringType),
    description = "Sort by"
  )

  def projectsField[T: Identifiable]: Field[GraphQLContext, T] =
    Field[GraphQLContext, T, Any, Any](
      "projects", OptionType(projectConnection),
      Some("List of projects"),
      arguments = IdArg :: NameArg :: Connection.Args.All,
      resolve = ctx ⇒ Connection.connectionFromSeq(AsyncUtils.await(ctx.ctx.allDAO.projectDAO.getAll), ConnectionArgs(ctx))
    )

  def topicsField[T: Identifiable](applyTopicFromValue: Boolean = false): Field[GraphQLContext, T] =
    Field[GraphQLContext, T, Any, Any](
      "topics", OptionType(topicConnection),
      Some("List of topics"),
      arguments = ProjectIdArg :: Connection.Args.All,
      resolve = ctx ⇒ Connection.connectionFromSeq(
        AsyncUtils.await(ctx.ctx.allDAO.topicDAO.where(
          ctx.ctx.allDAO.topicDAO.filterQuery(
            projectId = if (applyTopicFromValue) Option(implicitly[Identifiable[T]].id(ctx.value)) else ctx.argOpt("projectId"))
        )), ConnectionArgs(ctx)
      )
    )

  def repositoriesField[T: Identifiable](applyOwnerFromValue: Boolean = false): Field[GraphQLContext, T] =
    Field[GraphQLContext, T, Any, Any](
      "repositories", OptionType(repositoryConnection),
      Some("List of repositories"),
      arguments = IdArg :: NameArg :: OwnerArg :: OwnerName :: Connection.Args.All,
      resolve = ctx ⇒ {
        Connection.connectionFromSeq(
          AsyncUtils.await(ctx.ctx.repoDAO.getBy(ctx arg IdArg, ctx arg NameArg, ctx arg OwnerArg, ctx arg OwnerName)),
          ConnectionArgs(ctx)
        )
      })

  def repositoryField[T: Identifiable](applyOwnerFromValue: Boolean = false): Field[GraphQLContext, T] =
    Field[GraphQLContext, T, Any, Any](
      "repository", OptionType(RepositoryType),
      Some("Find repository."),
      arguments = IdArg :: NameArg :: OwnerArg :: OwnerName  :: Connection.Args.All,
      resolve = ctx ⇒ {
        AsyncUtils.await(ctx.ctx.repoDAO.getBy(ctx arg IdArg, ctx arg NameArg, ctx arg OwnerArg, ctx arg OwnerName)).headOption
      }
    )

  implicit val GitObjectIDType: ScalarType[ObjectId] = ScalarType[ObjectId](
    name = "GitObjectID",
    description = Some("A string representing GitObjectID."),
    coerceOutput = (u, caps) => u.getName,
    coerceUserInput = {
      case s: String => parseObjectId(s)
      case _ => Left(GitObjectIDCoercionViolation)
    },
    coerceInput = {
      case sangria.ast.StringValue(s, _, _) => parseObjectId(s)
      case _ => Left(GitObjectIDCoercionViolation)
    }
  )

  implicit val DiffChangeType = EnumType[ChangeType](
    name = "ChangeType",
    description = Some("Types of change made to file"),
    values = List(
      EnumValue("ADD", Some("File was added"), ChangeType.ADD),
      EnumValue("MODIFY", Some("File was modified"), ChangeType.MODIFY),
      EnumValue("DELETE", Some("File was deleted"), ChangeType.DELETE),
      EnumValue("RENAME", Some("File was renamed"), ChangeType.RENAME),
      EnumValue("COPY", Some("File was copied"), ChangeType.COPY)
    )
  )

  // TODO: add proper Violations to Inputs
  implicit val TimestampType = ScalarType[Timestamp]("Timestamp",
    description = Some(
      "The `Long` scalar type represents non-fractional signed whole numeric values. " +
        "Long can represent values between -(2^63) and 2^63 - 1."),
    coerceOutput = (value, _) ⇒ value.getTime,
    coerceUserInput = {
      case i: Int ⇒ Right(new Timestamp(i: Long))
      case i: Long ⇒ Right(new Timestamp(i))
      case i: BigInt if !i.isValidLong ⇒ Left(StringCoercionViolation)
      case i: BigInt ⇒ Right(new Timestamp(i.longValue))
      case _ ⇒ Left(StringCoercionViolation)
    },
    coerceInput = {
      case ast.IntValue(i, _, _) ⇒ Right(new Timestamp(i: Long))
      case ast.BigIntValue(i, _, _) if !i.isValidLong ⇒ Left(StringCoercionViolation)
      case ast.BigIntValue(i, _, _) ⇒ Right(new Timestamp(i.longValue))
      case _ ⇒ Left(StringCoercionViolation)
    })

  implicit val UserType: ObjectType[GraphQLContext, User] = deriveObjectType[GraphQLContext, User](
    ObjectTypeName("User"),
    ObjectTypeDescription("A user is an individual's account on Terrella that owns repositories and can make new content."),
    Interfaces(nodeInterface),
    ReplaceField("id", Node.globalIdField),
    ExcludeFields("password"),
    AddFields(
      Field("rawId", StringType,
        Some("User ID based on database"),
        resolve = ctx ⇒ implicitly[Identifiable[User]].id(ctx.value)
      ),
      Field("providerAccounts", OptionType(userProviderAccountConnection),
        Some("List of providers linked to the user"),
        arguments = ProviderOptArg :: Connection.Args.All,
        resolve = c => {
          Connection.connectionFromSeq(
            (c arg ProviderOptArg) match {
              case Some(provider) => {
                AsyncUtils.await(
                  c.ctx.allDAO.userDAO.getUserProviderAccount(provider, None, Some(c.value.id))
                ) match {
                  case Some(userProvider) => Seq[UserProviderAccount](userProvider)
                  case None => Seq[UserProviderAccount]()
                }
              }
              case None => {
                AsyncUtils.await(
                  c.ctx.allDAO.userDAO.getAllUserProviderAccount(c.value.id)
                )
              }
            }, ConnectionArgs(c)
          )
        }
      ),
      repositoriesField(applyOwnerFromValue = true),
      projectsField
    )
  )

  implicit val UserProviderAccountType: ObjectType[GraphQLContext, UserProviderAccount] = deriveObjectType[GraphQLContext, UserProviderAccount](
    ObjectTypeName("UserProviderAccount"),
    ObjectTypeDescription("Provider account linked to user"),
    Interfaces(nodeInterface),
    ReplaceField("accessToken", (
      Field("accessToken", OptionType(StringType), resolve = c => {
        try {
          if (c.ctx.viewer.get.id == c.value.userId) {
            c.value.accessToken
          } else {
            None
          }
        } catch {
          case _: Exception => None
        }
      })
    )),
    ReplaceField("firebaseToken", (
      Field("firebaseToken", OptionType(StringType), resolve = c => {
        try {
          if (c.ctx.viewer.get.id == c.value.userId) {
            c.value.firebaseToken
          } else {
            None
          }
        } catch {
          case _: Exception => None
        }
      })
    )),
    AddFields(Node.globalIdField)
  )

  implicit val ProjectType: ObjectType[GraphQLContext, Project] = deriveObjectType[GraphQLContext, Project](
    ObjectTypeName("Project"),
    ObjectTypeDescription("Repository project"),
    Interfaces(nodeInterface),
    ReplaceField("id", Node.globalIdField),
    AddFields(
      topicsField(applyTopicFromValue = true)
    )
  )

  implicit val TopicType: ObjectType[GraphQLContext, Topic] = deriveObjectType[GraphQLContext, Topic](
    ObjectTypeName("Topic"),
    ObjectTypeDescription("A topic aggregates entities that are related to a subject."),
    Interfaces(nodeInterface),
    ReplaceField("id", Node.globalIdField),
    AddFields(
      Field("value", StringType, resolve = ctx => ctx.value.id)
    )
  )

  implicit val GitActorType: ObjectType[GraphQLContext, GitActor] = deriveObjectType[GraphQLContext, GitActor](
    ObjectTypeName("GitActor"),
    ObjectTypeDescription("Represents an actor in a Git commit (ie. an author or committer).")
  )
  implicit val DiffInfoType: ObjectType[GraphQLContext, DiffInfo] = deriveObjectType[GraphQLContext, DiffInfo](
    ObjectTypeName("DiffInfo"),
    ObjectTypeDescription("Represents diff information between two object.")
  )

  implicit val RepositoryType: ObjectType[GraphQLContext, Repository] = deriveObjectType[GraphQLContext, Repository](
    ObjectTypeName("Repository"),
    ObjectTypeDescription("A repository contains the content for a project."),
    Interfaces(nodeInterface),
    ReplaceField("id", Node.globalIdField),
    AddFields(
      Field("rawId", StringType,
        Some("Repository ID on database"),
        resolve = ctx ⇒ implicitly[Identifiable[Repository]].id(ctx.value)
      ),
      Field("owner", OptionType(UserType),
        Some("The User owner of the repository."),
        resolve = ctx => AsyncUtils.await(ctx.ctx.allDAO.userDAO.getById(ctx.value.ownerId))
      ),
      Field("project", ProjectType,
        Some("Repository project"),
        resolve = ctx ⇒ AsyncUtils.await(
          ctx.ctx.allDAO.projectDAO.where(ctx.ctx.allDAO.projectDAO.filterQuery(repositoryId = Option(ctx.value.id)))
        ).head
      ),
      Field("stashes", OptionType(refConnection),
        Some("List of pending pushes on the repository by branch name"),
        arguments = RefPrefix :: IsOnlineArg :: Connection.Args.All,
        resolve = ctx ⇒ {
          val stashes = ctx.ctx.jGitUtil.getRepositoryRefs(ctx.value, ctx arg RefPrefix, true)
          val dbStashes = AsyncUtils.await(ctx.ctx.allDAO.stashDAO.getAll(ctx.value.id, ctx arg IsOnlineArg))
          val retStashes = stashes.filter { stash => dbStashes.exists { x => stash.name == s"refs/heads/stash-${x.stashNum}" } }
          UpdateCtx(
            Connection.connectionFromSeq(retStashes, ConnectionArgs(ctx))
          )(_ => ctx.ctx.copy(refsCount = Some(retStashes.length)))
        }
      ),
      Field("ref", OptionType(RefType),
        Some("Find a branch from the repository"),
        arguments = RefName :: Nil,
        resolve = ctx => ctx.ctx.jGitUtil.getRepositoryRef(ctx.value, ctx arg RefName)
      ),
      Field("refs", OptionType(refConnection),
        Some("List all branches from the repository"),
        arguments = RefPrefix :: Connection.Args.All,
        resolve = ctx ⇒ {
          val refs = ctx.ctx.jGitUtil.getRepositoryRefs(ctx.value, ctx arg RefPrefix, false)
          // get refs and also update ref prefix queried for total count
          UpdateCtx(
            Connection.connectionFromSeq(refs, ConnectionArgs(ctx))
          )(_ => ctx.ctx.copy(refsCount = Some(refs.length)))
        }
      ),
      Field("url", StringType,
        Some("Url of the repository on server, (ie. to use on git clone)"),
        resolve = c => c.ctx.jGitUtil.getRepositoryUrl(c.value)
      )
    )
  )

  implicit val GitObjectType: InterfaceType[GraphQLContext, GitObject] = InterfaceType(
    "GitObject",
    "Represents a Git object.",
    fields[GraphQLContext, GitObject](
      Field("oid", GitObjectIDType,
        Some("The Git object ID"),
        resolve = _.value.oid
      )
    )
  )

  implicit val StashType: ObjectType[GraphQLContext, Stash] = deriveObjectType[GraphQLContext, Stash](
    ObjectTypeName("Stash"),
    ObjectTypeDescription("Represents a Ref stash info"),
    Interfaces(nodeInterface),
    ReplaceField("id", Node.globalIdField),
    AddFields(
      Field("rawId", StringType, resolve = ctx ⇒ implicitly[Identifiable[Stash]].id(ctx.value)),
      Field("comments", OptionType(stashCommentConnection),
        Some("Parent comments for the stash"),
        arguments = SortByArg :: Connection.Args.All,
        resolve = c => {
          Connection.connectionFromSeq(
            AsyncUtils.await(
              c.ctx.allDAO.stashDAO.getParentComments(c.value.id, c arg SortByArg)
            ), ConnectionArgs(c)
          )
        }
      ),
      Field("votes", OptionType(stashVoteConnection),
        Some("Votes for the stash"),
        arguments = Connection.Args.All,
        resolve = c => {
          Connection.connectionFromSeq(
            AsyncUtils.await(
              c.ctx.allDAO.stashDAO.getVotes(c.value.id)
            ), ConnectionArgs(c)
          )
        }
      ),
      Field("acceptVotes", OptionType(stashVoteConnection),
        Some("Accept votes for the stash"),
        arguments = Connection.Args.All,
        resolve = c => {
          Connection.connectionFromSeq(
            AsyncUtils.await(
              c.ctx.allDAO.stashDAO.getAcceptVotes(c.value.id)
            ), ConnectionArgs(c)
          )
        }
      ),
      Field("rejectVotes", OptionType(stashVoteConnection),
        Some("Reject votes for the stash"),
        arguments = Connection.Args.All,
        resolve = c => {
          Connection.connectionFromSeq(
            AsyncUtils.await(
              c.ctx.allDAO.stashDAO.getRejectVotes(c.value.id)
            ), ConnectionArgs(c)
          )
        }
      ),
      Field("isUserVoted", OptionType(BooleanType),
        Some("Check if current logged user voted up the stash, true = vote up, false = vote down, None = no vote"),
        resolve = c => {
          c.ctx.viewer match {
            case Some(user) => {
              val stashVote = AsyncUtils.await(
                c.ctx.allDAO.stashDAO.getVoteByUser(c.value.id, user.id)
              )
              stashVote match {
                case Some(vote) => if (vote.isVoteUp) Some(true) else Some(false)
                case None => None
              }
            }
            case None => None
          }
        }
      ),
      Field("owner", OptionType(UserType),
        Some("The stash creator"),
        resolve = c => {
          AsyncUtils.await(c.ctx.allDAO.userDAO.getUserById(c.value.ownerId)).headOption
        }
      )
    )
  )

  implicit val RefType: ObjectType[GraphQLContext, Ref] = deriveObjectType[GraphQLContext, Ref](
    ObjectTypeName("Ref"),
    ObjectTypeDescription("Represents a Git reference. (branch / stash)"),
    Interfaces(nodeInterface),
    ReplaceField("id", Node.globalIdField),
    AddFields(
      Field("rawId", StringType, resolve = ctx ⇒ implicitly[Identifiable[Ref]].id(ctx.value)),
      Field("stash", OptionType(StashType),
        Some("Stash info if the ref is a stash"),
        resolve = c => {
          val stashNum = c.value.name.replaceAll("refs/heads/", "").stripPrefix("stash-").toInt
          AsyncUtils.await(c.ctx.allDAO.stashDAO.getByRepo(c.value.repository.id, stashNum))
        }
      ),
      Field("target", OptionType(GitObjectType),
        Some("The object the ref points to. Currently only points to the last commit of the branch / stash"),
        resolve = implicit ctx => {
          val revObject = ctx.ctx.jGitUtil.getRevObjectFromId(ctx.value.repository, ctx.value.oid)
          if (revObject.getType == Constants.OBJ_COMMIT) {
            Some(new Commit(
              revObject.asInstanceOf[RevCommit],
              implicitly[Identifiable[Ref]].id(ctx.value),
              ctx.value.repository,
              AsyncUtils.await(ctx.ctx.allDAO.userDAO.getById(ctx.value.repository.ownerId))
            ))
          } else {
            None
          }
        }
      ),
      Field("commit", OptionType(CommitType),
        Some("Find commit from the ref by commit ID"),
        arguments = CommitId :: Nil,
        resolve = ctx => {
          ctx.ctx.jGitUtil.getCommit(ctx.value.repository, implicitly[Identifiable[Ref]].id(ctx.value), ctx arg CommitId)
        }
      )
    )
  )

  implicit val CommitType: ObjectType[GraphQLContext, Commit] = deriveObjectType[GraphQLContext, Commit](
    ObjectTypeName("Commit"),
    ObjectTypeDescription("Represents a Git commit."),
    Interfaces(nodeInterface, GitObjectType),
    ReplaceField("id", Node.globalIdField),
    ExcludeFields("refId"),
    AddFields(
      Field("shortId", StringType,
        Some("Commit ID shortened into 7 chars"),
        resolve = ctx => ctx.value.id.slice(0, 7)
      ),
      Field("history", OptionType(commitConnection),
        Some("The linear commit history for a given ref or object"),
        arguments = PathOption :: isStash :: Connection.Args.All,
        resolve = ctx ⇒ {
          val refObj = Json.parse(ctx.value.refId)
          val refName = (refObj \ "refName").as[String]
          val range = ctx arg isStash match {
            case Some(r) => {
              if (r) {
                val repositoryId = (refObj \ "repoId").as[String]
                val stashNum = refName.stripPrefix("refs/heads/stash-").toInt
                val stash = AsyncUtils.await(ctx.ctx.allDAO.stashDAO.getByRepo(repositoryId, stashNum)).get
                Some((stash.baseOid, stash.currentOid))
              } else {
                None
              }
            }
            case None => None
          }
          val history = ctx.ctx.jGitUtil.getCommitHistory(ctx.value.repository, refName, (ctx arg PathOption), range).map(revCommit =>
            new Commit(revCommit, refName, ctx.value.repository, AsyncUtils.await(ctx.ctx.allDAO.userDAO.getById(ctx.value.repository.ownerId)))
          )
          // get commit history and also update context to contain history length for total count
          UpdateCtx(Connection.connectionFromSeq(history, ConnectionArgs(ctx)))(_ => ctx.ctx.copy(commitHistory = history))
        }
      ),
      Field("diff", ListType(DiffInfoType),
        Some("List of touched object diff under the commit"),
        resolve = ctx => {
          ctx.ctx.jGitUtil.getDiffs(ctx.value.repository, ctx.value.oid)
        }
      )
    )
  )

  implicit val TreeEntryType: ObjectType[GraphQLContext, TreeEntry] = deriveObjectType[GraphQLContext, TreeEntry](
    ReplaceField("object",
      Field("object", OptionType(GitObjectType),
        Some("Entry file object."),
        resolve = ctx => ctx.value.`object`
      )
    ),
    AddFields(
      Field("history", OptionType(commitConnection),
        Some("The linear commit history for the object"),
        arguments = RefName :: isStash :: Connection.Args.All,
        resolve = ctx ⇒ {
          val history = ctx.ctx.jGitUtil.getCommitHistory(ctx.value.repository, ctx arg RefName, Some(ctx.value.name)).map(revCommit =>
            new Commit(
              revCommit,
              ctx.ctx.jGitUtil.getRefId(ctx.value.repository.id, ctx arg RefName),
              ctx.value.repository,
              AsyncUtils.await(ctx.ctx.allDAO.userDAO.getById(ctx.value.repository.ownerId))
            )
          )
          UpdateCtx(Connection.connectionFromSeq(history, ConnectionArgs(ctx)))(_ => ctx.ctx.copy(commitHistory = history))
        }
      )
    )
  )

  implicit val TreeType: ObjectType[GraphQLContext, Tree] = deriveObjectType[GraphQLContext, Tree](
    ObjectTypeName("Tree"),
    ObjectTypeDescription("Represents a Git tree."),
    Interfaces(nodeInterface, GitObjectType),
    ReplaceField("id", Node.globalIdField),
    AddFields(
      Field("entries", ListType(TreeEntryType),
        Some("A list of tree entries."),
        arguments = PathOption :: Nil,
        resolve = ctx => ctx.ctx.jGitUtil.getTreeEntries(ctx.value.repository, ctx.value.oid, ctx arg PathOption)
      )
    )
  )

  implicit val BlobType: ObjectType[GraphQLContext, Blob] = deriveObjectType[GraphQLContext, Blob](
    ObjectTypeName("Blob"),
    ObjectTypeDescription("Represents a Git blob."),
    Interfaces(nodeInterface, GitObjectType),
    ReplaceField("id", Node.globalIdField)
  )

  implicit val StashCommentType: ObjectType[GraphQLContext, StashComment] = deriveObjectType[GraphQLContext, StashComment](
    ObjectTypeName("StashComment"),
    ObjectTypeDescription("User comments for a stash."),
    Interfaces(nodeInterface),
    ReplaceField("id", Node.globalIdField),
    AddFields(
      Field("rawId", StringType, resolve = c ⇒ implicitly[Identifiable[StashComment]].id(c.value)),
      Field("owner", OptionType(UserType),
        Some("The User owner of the comment."),
        resolve = ctx => AsyncUtils.await(ctx.ctx.allDAO.userDAO.getById(ctx.value.ownerId))
      ),
      Field("isOwnerVoteUp", OptionType(BooleanType),
        Some("Stash voting status for the current comment owner"),
        resolve = c => {
          AsyncUtils.await(c.ctx.allDAO.stashDAO.getVoteByUser(c.value.stashId, c.value.ownerId)) match {
            case Some(vote) => Some(vote.isVoteUp)
            case None => None
          }
        }
      ),
      Field("comments", OptionType(stashCommentConnection),
        Some("Child comments for the current comments"),
        arguments = SortByArg :: Connection.Args.All,
        resolve = c => {
          Connection.connectionFromSeq(
            AsyncUtils.await(
              c.ctx.allDAO.stashDAO.getChildComments(c.value.id, c arg SortByArg)
            ), ConnectionArgs(c)
          )
        }
      ),
      Field("isUserVoted", OptionType(BooleanType),
        Some("Check if current logged user voted up the comment stash, true = vote up, false = vote down, None = no vote"),
        resolve = c => {
          c.ctx.viewer match {
            case Some(user) => {
              val stashCommentVote = AsyncUtils.await(
                c.ctx.allDAO.stashDAO.getCommentVoteByUser(c.value.id, user.id)
              )
              stashCommentVote match {
                case Some(vote) => if (vote.isVoteUp) Some(true) else Some(false)
                case None => None
              }
            }
            case None => None
          }
        }
      )
    )
  )

  implicit val StashVoteType: ObjectType[GraphQLContext, StashVote] = deriveObjectType[GraphQLContext, StashVote](
    ObjectTypeName("StashVote"),
    ObjectTypeDescription("User vote for a stash."),
    Interfaces(nodeInterface),
    AddFields(
      Node.globalIdField,
      Field("rawId", StringType, resolve = c ⇒ implicitly[Identifiable[StashVote]].id(c.value)),
      Field("owner", OptionType(UserType),
        Some("The User owner of the vote."),
        resolve = ctx => AsyncUtils.await(ctx.ctx.allDAO.userDAO.getById(ctx.value.ownerId))
      )
    )
  )

  lazy implicit val ViewerType: ObjectType[GraphQLContext, Viewer] = deriveObjectType[GraphQLContext, Viewer](
    ObjectTypeName("Viewer"),
    ObjectTypeDescription("The current user"),
    Interfaces(nodeInterface),
    ReplaceField("id", Node.globalIdField),
    AddFields(
      repositoryField(),
      repositoriesField(),
      topicsField(),
      Field("stashes", OptionType(refConnection),
        Some("Find stashes by users"),
        arguments = OwnerArg :: OwnerName :: IsOnlineArg :: Connection.Args.All,
        resolve = {
          c => {
            val userId = (c arg OwnerArg) match {
              case Some(id) => Some(id)
              case None => {
                (c arg OwnerName) match {
                  case Some(name) => {
                    AsyncUtils.await(c.ctx.allDAO.userDAO.getByUserName(name)).headOption match {
                      case Some(user) => Some(user.id)
                      case None => {
                        (c.ctx.viewer) match {
                          case Some(user) => Some(user.id)
                          case None => None
                        }
                      }
                    }
                  }
                  case None => {
                    (c.ctx.viewer) match {
                      case Some(user) => Some(user.id)
                      case None => None
                    }
                  }
                }
              }
            }
            Connection.connectionFromSeq(
              userId match {
                case Some(id) => {                
                  val users = AsyncUtils.await(c.ctx.allDAO.stashDAO.getAllByUserId(id, c arg IsOnlineArg))
                  users.map {
                    stash => c.ctx.jGitUtil.getRefById(c.ctx.jGitUtil.getRefId(stash.repositoryId, s"stash-${stash.stashNum}"))
                  } filter { !_.isEmpty } map { _.get }
                }
                case None => Seq[Ref]()
              }, ConnectionArgs(c)
            )
          }
        }
      ),
      Field("actor", OptionType(UserType),
        Some("Git server user actor"),
        resolve = _.ctx.viewer
      ),
      Field("user", OptionType(UserType),
        Some("Terrella user"),
        arguments = IdArg :: LoginArg :: FirebaseIdArg :: Nil,
        resolve = {
          c => {
            (c arg IdArg) match {
              case Some(id) => AsyncUtils.await(c.ctx.allDAO.userDAO.getUserById(id)).headOption
              case None => (c arg LoginArg) match {
                case Some(userName) => AsyncUtils.await(c.ctx.allDAO.userDAO.getByUserName(userName)).headOption
                case None => (c arg FirebaseIdArg) match {
                  case Some(firebaseId) => AsyncUtils.await(c.ctx.allDAO.userDAO.getUserByFirebaseId(firebaseId))
                  case None => c.ctx.viewer
                }
              }
            }
          }
        }
      ),
      Field("users", OptionType(userConnection),
        Some("Find Terrella users"),
        arguments = IdArg :: LoginArg :: Connection.Args.All,
        resolve = {
          c => {
//          ctx => Connection.connectionFromSeq(
//            AsyncUtils.await(ctx.ctx.allDAO.userDAO.where(
//              ctx.ctx.allDAO.userDAO.filterQuery(login = ctx argOpt "login")
//            )), ConnectionArgs(ctx)
//          )
            (c arg IdArg) match {
              case Some(id) => Connection.connectionFromSeq(
                AsyncUtils.await(c.ctx.allDAO.userDAO.getUserById(id)), ConnectionArgs(c)
              )
              case None => (c arg LoginArg) match {
                case Some(userName) => Connection.connectionFromSeq(
                  AsyncUtils.await(c.ctx.allDAO.userDAO.getByUserName(userName)), ConnectionArgs(c)
                )
                // TODO: Return all users?
                case None => Connection.connectionFromSeq(Seq[User](), ConnectionArgs(c))
              }
            }
          }
        }
      ),
      Field("userProvider", OptionType(UserProviderAccountType),
        Some("User provider data"),
        arguments = UserIdOptArg :: ProviderOptArg :: ProviderIdArg :: FirebaseIdArg :: Nil,
        resolve = {
          c => {
            (c arg FirebaseIdArg) match {
              case Some(firebaseId) => AsyncUtils.await(c.ctx.allDAO.userDAO.getUserProviderAccountByFirebaseId(firebaseId))
              case None => AsyncUtils.await(c.ctx.allDAO.userDAO.getUserProviderAccount((c arg ProviderOptArg).get, c arg ProviderIdArg, c arg UserIdOptArg)) 
            }
          }
        }
      ),
      Field("accessToken", OptionType(StringType),
        Some("Current user access token"),
        resolve = {
          c => {
            try {
              AsyncUtils.await(c.ctx.allDAO.userDAO.getUserProviderAccount("stackexchange", None, Some(c.ctx.viewer.get.id))) match {
                case Some(userProvider) => userProvider.accessToken
                case None => None
              }
            } catch {
              case _: Exception => None
            }
          }
        }
      ),
      Field("firebaseToken", OptionType(StringType),
        Some("Current user firebase token"),
        resolve = {
          c => {
            try {
              AsyncUtils.await(c.ctx.allDAO.userDAO.getUserProviderAccount("stackexchange", None, Some(c.ctx.viewer.get.id))) match {
                case Some(userProvider) => userProvider.firebaseToken
                case None => None
              }
            } catch {
              case _: Exception => None
            }
          }
        }
      )
    )
  )

  val ConnectionDefinition(userEdge, userConnection) = Connection.definition[GraphQLContext, Connection, User]("User", UserType)
  val ConnectionDefinition(userProviderAccountEdge, userProviderAccountConnection) =
    Connection.definition[GraphQLContext, Connection, UserProviderAccount]("UserProviderAccount", UserProviderAccountType)
  val ConnectionDefinition(repositoryEdge, repositoryConnection) = Connection.definition[GraphQLContext, Connection, Repository]("Repository", RepositoryType)
  val ConnectionDefinition(projectEdge, projectConnection) = Connection.definition[GraphQLContext, Connection, Project]("Project", ProjectType)
  val ConnectionDefinition(topicEdge, topicConnection) = Connection.definition[GraphQLContext, Connection, Topic]("Topic", TopicType)
  val ConnectionDefinition(_, refConnection) = Connection.definition[GraphQLContext, Connection, Ref]("Ref", RefType, connectionFields = fields(
    // count refs present in repo of current context
    Field("totalCount", OptionType(IntType), resolve = ctx => {
      ctx.ctx.refsCount
    })
  ))
  val ConnectionDefinition(_, commitConnection) = Connection.definition[GraphQLContext, Connection, Commit]("Commit", CommitType, connectionFields = fields(
    Field("totalCount", OptionType(IntType), resolve = ctx => {
      ctx.ctx.commitHistory.length
    }),
    Field("totalContributors", OptionType(IntType), resolve = ctx => {
      ctx.ctx.commitHistory.map(commit => commit.author.user.get.userName).distinct.length
    }),
    Field("contributors", ListType(UserType), resolve = ctx => {
      ctx.ctx.commitHistory.map(commit => commit.author.user.get).distinct
    })
  ))
  val ConnectionDefinition(_, stashCommentConnection) =
    Connection.definition[GraphQLContext, Connection, StashComment]("StashComment", StashCommentType, connectionFields = fields(
      Field("totalCount", OptionType(IntType), resolve = ctx => {
        ctx.value.edges.length
      }),
      Field("totalAllCount", OptionType(IntType), resolve = ctx => {
        try {
          Some(AsyncUtils.await(ctx.ctx.allDAO.stashDAO.getAllCommentsCount(ctx.value.edges.head.node.stashId)))
        } catch {
          case _: Exception => None
        }
      })
    )
  )
  val ConnectionDefinition(_, stashVoteConnection) =
    Connection.definition[GraphQLContext, Connection, StashVote]("StashVote", StashVoteType, connectionFields = fields(
      Field("totalCount", OptionType(IntType), resolve = ctx => {
        ctx.value.edges.length
      }),
      Field("totalVotePoints", FloatType, resolve = ctx => {
        val voteUpPoint = ctx.value.edges.filter(f => f.node.isVoteUp).foldLeft(0.0)((x, y) => x + y.node.votePoint)
        val voteDownPoint = ctx.value.edges.filter(f => !f.node.isVoteUp).foldLeft(0.0)((x, y) => x + y.node.votePoint)
        voteUpPoint - voteDownPoint
      })
    )
  )

  case class CreateRepositoryMutationPayload(clientMutationId: Option[String], repository: Repository, repositoryEdge: Edge[Repository]) extends Mutation

  lazy val createRepositoryMutation = Mutation.fieldWithClientMutationId[GraphQLContext, Unit, CreateRepositoryMutationPayload, InputObjectType.DefaultInput](
    fieldName = "createRepository",
    typeName = "CreateRepository",
    inputFields = List(
      InputField("name", StringType),
      InputField("ownerUserName", StringType),
      InputField("isPrivate", BooleanType),
      InputField("isPushVote", BooleanType),
      InputField("isReviewStash", OptionInputType(BooleanType)),
      InputField("goals", OptionInputType(StringType)),
      InputField("description", OptionInputType(StringType)),
      InputField("topics", OptionInputType(ListInputType(StringType)))
    ),
    outputFields = fields(
      Field("repositoryEdge", repositoryEdge, resolve = _.value.repositoryEdge),
      Field("repository", OptionType(RepositoryType), resolve = ctx => ctx.value.repository),
      Field("viewer", ViewerType, resolve = _ => Viewer())
    ),
    mutateAndGetPayload = (input, ctx) => {
      val name = input("name").asInstanceOf[String]
      val ownerUserName = input("ownerUserName").asInstanceOf[String]
      val isPrivate = input("isPrivate").asInstanceOf[Boolean]
      val isPushVote = input("isPushVote").asInstanceOf[Boolean]
      val isReviewStash = input.getOrElse("isReviewStash", None).asInstanceOf[Option[Boolean]].getOrElse(true)

      val goals = input.getOrElse("goals", None).asInstanceOf[Option[String]]
      val description = input.getOrElse("description", None).asInstanceOf[Option[String]]
      val topics = input.getOrElse("topics", None).asInstanceOf[Option[Seq[String]]]

      AsyncUtils.await(ctx.ctx.allDAO.userDAO.getUserName(ownerUserName)) match {
        case Some(user) =>
          ctx.ctx.jGitUtil.initRepository(user.userName, name)
          val newRepoDao = Repository(RandomGenerator.randomUUIDString, name, user.id, isPrivate, isPushVote, isReviewStash)
          AsyncUtils.await(ctx.ctx.repoDAO.create(newRepoDao))

          val newProject = Project(RandomGenerator.randomUUIDString, newRepoDao.name, user.id, newRepoDao.id, goals, description)
          AsyncUtils.await(ctx.ctx.allDAO.projectDAO.create(newProject))

          topics match {
            case Some(topicIds) => {
              AsyncUtils.await(ctx.ctx.allDAO.topicDAO.createMultiple(topicIds))
              AsyncUtils.await(ctx.ctx.allDAO.projectDAO.addTopics(newProject.id, topicIds))
            }
            case _ =>
          }

          val newRepo = Repository(newRepoDao.id, newRepoDao.name, user.id, newRepoDao.isPrivate, newRepoDao.isReviewStash, newRepoDao.isPushVote, newRepoDao.createdAt)

          val repoCursor = sangria.relay.Connection.cursorForObjectInConnection(
            ctx.ctx.repoDAO.all,
            newRepo
          ).toString()

          CreateRepositoryMutationPayload(Some(newRepo.id), newRepo, sangria.relay.Edge(newRepo, repoCursor))
        case None => null
      }
    }
  )

  case class CreateUserMutationPayload(clientMutationId: Option[String], user: Option[User], firebaseToken: Option[String]) extends Mutation


  val addUserProviderMutation = Mutation.fieldWithClientMutationId[GraphQLContext, Unit, CreateUserMutationPayload, InputObjectType.DefaultInput](
    fieldName = "addUserProvider",
    typeName = "AddUserProvider",
    inputFields = List(
      InputField("userId", StringType),
      InputField("provider", StringType),
      InputField("userName", StringType),
      InputField("providerId", OptionInputType(StringType)),
      InputField("accessToken", OptionInputType(StringType)),
      InputField("firebaseId", StringType)
    ),
    outputFields = fields(
      Field("user", OptionType(UserType), resolve = ctx ⇒ ctx.value.user),
      Field("firebaseToken", OptionType(StringType), resolve = ctx => ctx.value.firebaseToken)
    ),
    mutateAndGetPayload = (input, ctx) ⇒ {
      val userId = input("userId").asInstanceOf[String]
      val provider = input("provider").asInstanceOf[String]
      val userName = input("userName").asInstanceOf[String]
      val providerId = input("providerId").asInstanceOf[Option[String]]
      val accessToken = input("accessToken").asInstanceOf[Option[String]]
      val firebaseId = input("firebaseId").asInstanceOf[String]

      try {
        val user = AsyncUtils.await(ctx.ctx.allDAO.userDAO.getUserById(userId))
        if (user.length > 0) {
          val firebaseToken = FirebaseUtil.createCustomToken(provider, firebaseId)     
          val newUserProvider = UserProviderAccount(
            firebaseId, userId, provider, userName, providerId, accessToken, firebaseToken
          )
          AsyncUtils.await(ctx.ctx.allDAO.userDAO.insertUserProvider(newUserProvider))
          CreateUserMutationPayload(Some(user(0).id), Some(user(0)), None)
        } else {
          CreateUserMutationPayload(None, None, None)
        }
      } catch {
        case _: Exception => CreateUserMutationPayload(None, None, None)
      }
    }
  )

  val createUserMutation = Mutation.fieldWithClientMutationId[GraphQLContext, Unit, CreateUserMutationPayload, InputObjectType.DefaultInput](
    fieldName = "createUser",
    typeName = "CreateUser",
    inputFields = List(
      InputField("userId", OptionInputType(StringType)),
      InputField("userName", StringType),
      InputField("provider", StringType),
      InputField("providerId", OptionInputType(StringType)),
      InputField("accessToken", OptionInputType(StringType)),
      InputField("fullName", OptionInputType(StringType)),
      InputField("photoUrl", OptionInputType(StringType)),
      InputField("emailAddress", OptionInputType(StringType)),
      InputField("password", StringType),
      InputField("firebaseId", StringType)
    ),
    outputFields = fields(
      Field("user", OptionType(UserType), resolve = ctx ⇒ ctx.value.user),
      Field("firebaseToken", OptionType(StringType), resolve = ctx => ctx.value.firebaseToken)
    ),
    mutateAndGetPayload = (input, ctx) ⇒ {
      val userName = input("userName").asInstanceOf[String]
      val fullName = input("fullName").asInstanceOf[Option[String]]
      val photoUrl = input("photoUrl").asInstanceOf[Option[String]]
      val emailAddress = input("emailAddress").asInstanceOf[Option[String]]
      val password = input("password").asInstanceOf[String]

      val provider = input("provider").asInstanceOf[String]
      val providerId = input("providerId").asInstanceOf[Option[String]]
      val accessToken = input("accessToken").asInstanceOf[Option[String]]
      val firebaseId = input("firebaseId").asInstanceOf[String]

//      val newUser = AsyncUtils.await(ctx.ctx.allDAO.userDAO.create(User(firebaseId, userName, fullName, photoUrl, emailAddress, password)))
//      CreateUserMutationPayload(Some(newUser.id), newUser.id, newUser.userName, newUser.fullName, newUser.photoUrl, newUser.emailAddress, newUser.password)

      try {
        if (ctx.ctx.gitBlitUtil.createUser(userName, password)) {
          val newUser = User(firebaseId, userName, fullName, photoUrl, emailAddress, password)
          AsyncUtils.await(ctx.ctx.allDAO.userDAO.create(newUser))
          val firebaseToken = FirebaseUtil.createCustomToken(provider, firebaseId)          
          val newUserProvider = UserProviderAccount(
            firebaseId, firebaseId, provider, fullName.getOrElse(""), providerId, accessToken, firebaseToken
          )
          AsyncUtils.await(ctx.ctx.allDAO.userDAO.insertUserProvider(newUserProvider))
          CreateUserMutationPayload(Some(newUser.id), Some(newUser), firebaseToken)
        } else {
          CreateUserMutationPayload(None, None, None)
        }
      } catch {
        case _:Exception => CreateUserMutationPayload(None, None, None)
      }
    }
  )

  case class MergeStashMutationPayload(clientMutationId: Option[String], repository: Option[Repository]) extends Mutation

  val mergeStash = Mutation.fieldWithClientMutationId[GraphQLContext, Unit, MergeStashMutationPayload, InputObjectType.DefaultInput](
    fieldName = "mergeStash",
    typeName = "MergeStash",
    inputFields = List(
      InputField("repositoryId", StringType),
      InputField("stashName", StringType)),
    outputFields = fields(
      Field("repository", OptionType(RepositoryType), resolve = ctx => ctx.value.repository)
    ),
    mutateAndGetPayload = (input, ctx) ⇒ {
      val repositoryId = input("repositoryId").asInstanceOf[String]
      val stashName = input("stashName").asInstanceOf[String]

      ctx.ctx.repoDAO.find(repositoryId) match {
        case Some(repo) => {
          val merge = ctx.ctx.jGitUtil.mergeStash(repo, stashName)
          val refId = ctx.ctx.jGitUtil.getRefId(repositoryId, stashName)
          val ref = ctx.ctx.jGitUtil.getRefById(refId)
          MergeStashMutationPayload(Some(repositoryId + stashName), Some(repo))
        }
        case None => MergeStashMutationPayload(None, None)
      }
    }
  )

  case class StashMutationPayload(clientMutationId: Option[String], stash: Option[Stash]) extends Mutation

  val addStashCommentMutation = Mutation.fieldWithClientMutationId[GraphQLContext, Unit, StashMutationPayload, InputObjectType.DefaultInput](
    fieldName = "addStashComment",
    typeName = "AddStashComment",
    inputFields = List(
      InputField("stashId", StringType),
      InputField("content", StringType),
      InputField("parentId", OptionInputType(StringType))
    ),
    outputFields = fields(
      Field("stash", OptionType(StashType), resolve = ctx ⇒ ctx.value.stash)
    ),
    mutateAndGetPayload = (input, ctx) ⇒ {
      val stashId = input("stashId").asInstanceOf[String]
      val content = input("content").asInstanceOf[String]
      val parentId = if (input.contains("parentId")) { input("parentId").asInstanceOf[Option[String]] } else { None }

      try {
        val ownerId = ctx.ctx.viewer.get.id
        val newStashCommentId = RandomGenerator.randomUUIDString
        val newStashComment = StashComment(
          newStashCommentId, content, ownerId, stashId, parentId
        )
        AsyncUtils.await(ctx.ctx.allDAO.stashDAO.insertComment(newStashComment))

        val stash = AsyncUtils.await(ctx.ctx.allDAO.stashDAO.getById(stashId))
        StashMutationPayload(Some(newStashCommentId), stash)
      } catch {
        case _: Exception => StashMutationPayload(None, None)
      }
    }
  )

  val voteStashMutation = Mutation.fieldWithClientMutationId[GraphQLContext, Unit, StashMutationPayload, InputObjectType.DefaultInput](
    fieldName = "voteStash",
    typeName = "voteStash",
    inputFields = List(
      InputField("stashId", StringType),
      InputField("isVoteUp", BooleanType)
    ),
    outputFields = fields(
      Field("stash", OptionType(StashType), resolve = ctx ⇒ ctx.value.stash)
    ),
    mutateAndGetPayload = (input, ctx) ⇒ {
      val stashId = input("stashId").asInstanceOf[String]
      val isVoteUp = input("isVoteUp").asInstanceOf[Boolean]

      try {
        val owner = ctx.ctx.viewer.get
        val newStashVote = StashVote(
          owner.id, stashId, owner.reputation, isVoteUp
        )
        AsyncUtils.await(ctx.ctx.allDAO.stashDAO.getVoteByUser(stashId, owner.id)) match {
          case Some(userVote) => {
            if (userVote.isVoteUp == isVoteUp) {
              AsyncUtils.await(ctx.ctx.allDAO.stashDAO.deleteVote(stashId, owner.id))
            } else {
              AsyncUtils.await(ctx.ctx.allDAO.stashDAO.updateVote(stashId, owner.id, isVoteUp))
            }
          }
          case None => {
            AsyncUtils.await(ctx.ctx.allDAO.stashDAO.insertVote(newStashVote))
          }
        }

        val stash = AsyncUtils.await(ctx.ctx.allDAO.stashDAO.getById(stashId))
        StashMutationPayload(Some(owner.id + stashId), stash)
      } catch {
        case e: Exception => {
          StashMutationPayload(None, None)
        }
      }
    }
  )

  val voteStashCommentMutation = Mutation.fieldWithClientMutationId[GraphQLContext, Unit, StashMutationPayload, InputObjectType.DefaultInput](
    fieldName = "voteStashComment",
    typeName = "voteStashComment",
    inputFields = List(
      InputField("stashCommentId", StringType),
      InputField("stashId", StringType),
      InputField("isVoteUp", BooleanType)
    ),
    outputFields = fields(
      Field("stash", OptionType(StashType), resolve = ctx ⇒ ctx.value.stash)
    ),
    mutateAndGetPayload = (input, ctx) ⇒ {
      val stashCommentId = input("stashCommentId").asInstanceOf[String]
      val stashId = input("stashId").asInstanceOf[String]
      val isVoteUp = input("isVoteUp").asInstanceOf[Boolean]

      try {
        val owner = ctx.ctx.viewer.get
        val newStashCommentVote = StashCommentVote(
          owner.id, stashCommentId, owner.reputation, isVoteUp
        )
        AsyncUtils.await(ctx.ctx.allDAO.stashDAO.getCommentVoteByUser(stashCommentId, owner.id)) match {
          case Some(userVote) => {
            if (userVote.isVoteUp == isVoteUp) {
              AsyncUtils.await(ctx.ctx.allDAO.stashDAO.deleteCommentVote(userVote))
            } else {
              AsyncUtils.await(ctx.ctx.allDAO.stashDAO.updateCommentVote(userVote, isVoteUp))
            }
          }
          case None => {
            AsyncUtils.await(ctx.ctx.allDAO.stashDAO.insertCommentVote(newStashCommentVote))
          }
        }

        val stash = AsyncUtils.await(ctx.ctx.allDAO.stashDAO.getById(stashId))
        StashMutationPayload(Some(owner.id + stashCommentId), stash)
      } catch {
        case e: Exception => {
          StashMutationPayload(None, None)
        }
      }
    }
  )

  case class SetIsInvisibleMutationPayload(clientMutationId: Option[String], user: Option[User]) extends Mutation

  val setIsInvisible = Mutation.fieldWithClientMutationId[GraphQLContext, Unit, SetIsInvisibleMutationPayload, InputObjectType.DefaultInput](
    fieldName = "setIsInvisible",
    typeName = "SetIsInvisible",
    inputFields = List(
      InputField("isInvisible", BooleanType)
    ),
    outputFields = fields(
      Field("user", OptionType(UserType), resolve = ctx => ctx.value.user)
    ),
    mutateAndGetPayload = (input, ctx) ⇒ {
      val isInvisible = input("isInvisible").asInstanceOf[Boolean]
      
      try {
        val currentUser = ctx.ctx.viewer.get
        if (AsyncUtils.await(ctx.ctx.allDAO.userDAO.updateIsInvisible(currentUser.id, isInvisible)) == 1) {
          SetIsInvisibleMutationPayload(Some(currentUser.id), Some(currentUser))
        } else {
          SetIsInvisibleMutationPayload(None, None)
        }
      }
      catch {
        case e: Exception => SetIsInvisibleMutationPayload(None, None)
      }
    }
  )

  case class SetStashIsOnlineMutationPayload(clientMutationId: Option[String], ref: Option[Ref]) extends Mutation

  val setStashIsOnline = Mutation.fieldWithClientMutationId[GraphQLContext, Unit, SetStashIsOnlineMutationPayload, InputObjectType.DefaultInput](
    fieldName = "setStashIsOnline",
    typeName = "SetStashIsOnline",
    inputFields = List(
      InputField("stashId", StringType),
      InputField("isOnline", BooleanType)
    ),
    outputFields = fields(
      Field("ref", OptionType(RefType), resolve = ctx => ctx.value.ref)
    ),
    mutateAndGetPayload = (input, ctx) ⇒ {
      val stashId = input("stashId").asInstanceOf[String]
      val isOnline = input("isOnline").asInstanceOf[Boolean]
      
      try {
        val currentUser = ctx.ctx.viewer.get
        val stash = AsyncUtils.await(ctx.ctx.allDAO.stashDAO.getById(stashId)).get
        val ref = ctx.ctx.jGitUtil.getRefById(ctx.ctx.jGitUtil.getRefId(stash.repositoryId, s"stash-${stash.stashNum}"))
        println(ref)
        if (stash.ownerId == currentUser.id) {
          if (AsyncUtils.await(ctx.ctx.allDAO.stashDAO.updateIsOnline(stashId, isOnline)) == 1) {
            SetStashIsOnlineMutationPayload(Some(stash.id), ref)
          } else {
            SetStashIsOnlineMutationPayload(None, None)
          }
        } else {
          SetStashIsOnlineMutationPayload(None, None)
        }
      }
      catch {
        case e: Exception => SetStashIsOnlineMutationPayload(None, None)
      }
    }
  )
  
  val updateStashMeta = Mutation.fieldWithClientMutationId[GraphQLContext, Unit, StashMutationPayload, InputObjectType.DefaultInput](
    fieldName = "updateStashMeta",
    typeName = "UpdateStashMeta",
    inputFields = List(
      InputField("stashId", StringType),
      InputField("title", OptionInputType(StringType)),
      InputField("description", OptionInputType(StringType))
    ),
    outputFields = fields(
      Field("stash", OptionType(StashType), resolve = ctx => ctx.value.stash)
    ),
    mutateAndGetPayload = (input, ctx) ⇒ {
      val stashId = input("stashId").asInstanceOf[String]
      val title = input("title").asInstanceOf[Option[String]]
      val description = input("description").asInstanceOf[Option[String]]
      
      try {
        val currentUser = ctx.ctx.viewer.get
        val stash = AsyncUtils.await(ctx.ctx.allDAO.stashDAO.getById(stashId)).get
        if (stash.ownerId == currentUser.id) {
          if (AsyncUtils.await(ctx.ctx.allDAO.stashDAO.updateMeta(stashId, title, description)) == 1) {
            StashMutationPayload(Some(stash.id), AsyncUtils.await(ctx.ctx.allDAO.stashDAO.getById(stashId)))
          } else {
            StashMutationPayload(None, None)
          }
        } else {
          StashMutationPayload(None, None)
        }
      }
      catch {
        case e: Exception => StashMutationPayload(None, None)
      }
    }
  )
  
  val MutationType = ObjectType(
    "Mutation",
    "Mutate Data",
    fields[GraphQLContext, Unit](
      createRepositoryMutation,
      createUserMutation,
      addUserProviderMutation,
      mergeStash,
      addStashCommentMutation,
      voteStashMutation,
      voteStashCommentMutation,
      setIsInvisible,
      setStashIsOnline,
      updateStashMeta
    )
  )

  val Query = ObjectType(
    "Query",
    "The query root of Terrella's GraphQL interface.",
    fields[GraphQLContext, Unit](
      Field("viewer", ViewerType,
        Some("The current user."),
        resolve = _ ⇒ Viewer()
      ),
      nodeField,
      nodesField
    )
  )

  val schema = Schema(Query, Some(MutationType), additionalTypes = CommitType :: TreeType :: BlobType :: Nil)
}
