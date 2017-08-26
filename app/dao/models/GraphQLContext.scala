package dao.models

import dao.{DAOContainer}
import dao.RepositoryDAO
import utils.{GitblitUtil, JGitUtil}

case class GraphQLContext(
  repoDAO: RepositoryDAO,
  jGitUtil: JGitUtil,
  gitBlitUtil: GitblitUtil,
  allDAO: DAOContainer,
  viewer: Option[User],
  repoOpt: Option[Repository] = None,
  refPrefix: Option[String] = None,
  commitHistory: Seq[Commit] = Seq(),
  refsCount: Option[Int] = None
)
