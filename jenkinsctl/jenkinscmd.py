import click
import jenkins_job


def create_app_flow():
    params = {}
    name = click.prompt('Enter your project name')
    params["github_project_url"] = click.prompt(
      'Enter the github_project_url e.g. https://github.com/wizeline/standard-cicd-pipeline/')
    params["git_repo_url"] = click.prompt(
      'Enter the git_repo_url e.g. git@github.com:wizeline/wz-statuspage.git')
    params["docker_image_name"] = click.prompt(
      'Enter the docker_image_name e.g. cachet-backend')
    params["docker_source_rel_path"] = click.prompt(
      'Enter the docker_source_rel_path', default=".")
    params["slack_channel_name"] = click.prompt(
      'Enter the slack_channel_name', default="jenkins")

    click.echo("\nA Jenkins proyect will be created with these params:")
    click.echo(f" - name: {name}")
    for k, v in params.items():
        click.echo(f" - {k}: {v}")
    if not click.confirm("\nDo you want to continue?", abort=True):
        return
    gaf = GenericAppFlow(prefix=name)
    gaf.set_parameters(params)
    gaf.create()


def create_jenkins_flow():
    pass


@click.group()
@click.pass_context
def cli(ctx):
    pass


@cli.command()
@click.option('--jenkins_url', envvar='JENKINS_URL')
@click.option('--jenkins_user', envvar='JENKINS_USER')
@click.option('--jenkins_token', envvar='JENKINS_TOKEN')
@click.argument('job_type')
@click.pass_context
def create(ctx, jenkins_url, jenkins_user, jenkins_token, job_type):
    error = False
    if not jenkins_url:
        click.echo("missing --jenkins_url or env JENKINS_URL")
        error = True
    if not jenkins_user:
        click.echo("missing --jenkins_user or env JENKINS_USER")
        error = True
    if not jenkins_token:
        click.echo("missing --jenkins_token or env JENKINS_TOKEN")
        error = True
    if error:
        return

    if job_type == "app-flow":
        create_app_flow()
        return
    click.echo("Available job types: app-flow")


if __name__ == '__main__':
    cli()
