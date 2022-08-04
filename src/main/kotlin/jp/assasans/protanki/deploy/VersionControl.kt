package jp.assasans.protanki.deploy

import mu.KotlinLogging
import org.eclipse.jgit.api.Git
import org.eclipse.jgit.lib.Constants
import org.eclipse.jgit.lib.ObjectId
import org.eclipse.jgit.revwalk.RevCommit
import java.io.Closeable
import java.nio.file.Path

// TODO(Assasans): Git-specific
interface IVersionControl {
  val root: Path

  var remote: String
  var branch: String

  suspend fun getCommits(): Iterable<RevCommit>
  suspend fun getCurrentCommit(): RevCommit
  suspend fun getLastRemoteCommit(): RevCommit
  suspend fun checkout(id: ObjectId)
}

class GitVersionControl(
  override val root: Path,
  override var remote: String,
  override var branch: String
) : IVersionControl, Closeable {
  private val logger = KotlinLogging.logger {}

  private val git = Git.open(root.toFile())

  override suspend fun getCommits(): Iterable<RevCommit> {
    val commits = git.log().all().call()
    // for(commit in commits) {
    //   logger.info { "Commit ${commit.name.take(8)} - ${commit.shortMessage}" }
    // }
    return commits
  }

  override suspend fun getCurrentCommit(): RevCommit {
    val id = git.repository.resolve(Constants.HEAD)
    val commit = git.repository.parseCommit(id)

    logger.info { "Current HEAD: ${commit.name.take(8)} - ${commit.shortMessage}" }

    return commit
  }

  override suspend fun getLastRemoteCommit(): RevCommit {
    val result = git.fetch()
      .setRemote(remote)
      .setRefSpecs("refs/heads/$branch:refs/remotes/$remote/$branch")
      .setCheckFetchedObjects(true)
      .call()
    val update = result.getTrackingRefUpdate("refs/remotes/$remote/$branch")
    val remoteId = if(update == null) {
      logger.info { "No remote updates" }
      git.repository.resolve("refs/remotes/$remote/$branch")
    } else {
      logger.info { "Fetch: ${update.oldObjectId.name.take(8)}..${update.newObjectId.name.take(8)}" }
      update.newObjectId
    }

    val remoteCommit = git.repository.parseCommit(remoteId)
    logger.info { "Last commit: ${remoteCommit.name.take(8)} - ${remoteCommit.shortMessage}" }

    return remoteCommit
  }

  override suspend fun checkout(id: ObjectId) {
    // git.reset()
    //   .setMode(ResetType.HARD)
    //   .setRef("refs/remotes/$remote/$branch")
    //   .call()
    // logger.info { "Reset to remote HEAD" }

    git.checkout()
      .setName(id.name)
      .setForced(true)
      .call()
    logger.info { "Checked out commit: ${id.name.take(8)}" }
  }

  override fun close() {
    git.close()
  }
}
