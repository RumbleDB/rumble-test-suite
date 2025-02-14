import pandas as pd
import matplotlib.pyplot as plt
import os

def plot_df(df, title):
    names = df.index
    passes = df['pass']
    fails = df['fail']
    errors = df['error']
    skips = df['skip']

    # Calculate cumulative sums for stacking
    cumulative_passes = passes
    cumulative_fails = cumulative_passes + fails
    cumulative_errors = cumulative_fails + errors
    cumulative_skips = cumulative_errors + skips

    # Calculate the total counts for all tests
    total_passes = passes.sum()
    total_fails = fails.sum()
    total_errors = errors.sum()
    total_skips = skips.sum()

    # Calculate cumulative sums for the total bar
    total_cumulative_passes = total_passes
    total_cumulative_fails = total_cumulative_passes + total_fails
    total_cumulative_errors = total_cumulative_fails + total_errors

    # Create subplots with different heights
    fig, (ax1, ax2) = plt.subplots(2, 1, figsize=(10, 4), gridspec_kw={'height_ratios': [1, len(df)]})

    # Plotting the second plot (Total Test Results) above
    # Plot each bar segment
    ax1.barh('Total', total_passes, color='green', label='Pass')
    ax1.barh('Total', total_fails, left=total_cumulative_passes, color='red', label='Fail')
    ax1.barh('Total', total_errors, left=total_cumulative_fails, color='darkred', label='Error')
    ax1.barh('Total', total_skips, left=total_cumulative_errors, color='gray', label='Skip')

    # Add numbers to the total bar segments
    ax1.text(total_passes / 2, 'Total', str(total_passes), va='center', ha='center', color='white', fontweight='bold')
    ax1.text(total_cumulative_passes + total_fails / 2 - 300, 'Total', str(total_fails), va='center', ha='center', color='white', fontweight='bold')
    ax1.text(total_cumulative_fails + total_errors / 2 + 300, 'Total', str(total_errors), va='center', ha='center', color='white', fontweight='bold')
    ax1.text(total_cumulative_errors + total_skips / 2, 'Total', str(total_skips), va='center', ha='center', color='white', fontweight='bold')


    # Add labels and legend
    ax1.set_xlabel('Number of Tests')
    ax1.set_title(title)

    # Plotting the first plot (Detailed Test Results)
    # Plot each bar segment
    ax2.barh(names, passes, color='green', label='Pass')
    ax2.barh(names, fails, left=cumulative_passes, color='red', label='Fail')
    ax2.barh(names, errors, left=cumulative_fails, color='darkred', label='Error')
    ax2.barh(names, skips, left=cumulative_errors, color='gray', label='Skip')

    # Add labels and legend
    ax2.set_xlabel('Number of Tests')
    ax2.set_title('Individual Testset Results')
    ax2.legend()

    plt.tight_layout()
    os.makedirs('plots', exist_ok=True)
    plt.savefig(f"plots/{title}.png")

df = pd.read_json(f"analytics_results/count.json").set_index("name").sort_index()
plot_df(df, "Test Breakdown")
print(df.sum())

def plot_categories(df, cutoff, formatter, plot_settings):
    # format x labels
    df["msg"] = df["msg"].map(formatter)

    # add bar with all other ones summed up
    other_count = df[df["cnt"] < cutoff]["cnt"].sum()
    plot_df = pd.concat([df[df["cnt"] >= cutoff], pd.DataFrame([{'msg': 'Other', 'cnt': other_count}])], ignore_index=True)

    fig, ax = plt.subplots(figsize=(12, 6))
    plot_df.plot.bar(x="msg", y="cnt", ax=ax, color=plot_settings["color"], edgecolor='black', legend=False)

    # Lipstick
    ax.set_title(plot_settings["title"], fontsize=21)
    ax.set_xlabel(plot_settings["xlabel"], fontsize=15)
    ax.set_ylabel('Count', fontsize=15)
    ax.set_xticklabels(plot_df['msg'], rotation=45, ha='right', fontsize=12)
    ax.tick_params(axis='y', labelsize=12)
    ax.spines['top'].set_visible(False)
    ax.spines['right'].set_visible(False)

    # Add value labels on top of the bars
    for p in ax.patches:
        ax.annotate(f'{p.get_height()}', (p.get_x() + p.get_width() / 2., p.get_height()),
                    ha='center', va='center', xytext=(0, 10), textcoords='offset points', fontsize=12, color='black')

    plt.tight_layout()
    plt.savefig(f"plots/{plot_settings["filepath"]}", dpi=300)


def error_formatter(input_string):
    return input_string.split(".")[-1]

def fail_formatter(input_string):
    intermediate = input_string
    intermediate = intermediate.replace("&lt;", " ")
    intermediate = intermediate.replace("&gt;", "")
    intermediate = intermediate.replace("[", "")
    intermediate = intermediate.replace("]", "")
    return intermediate

def skip_formatter(input_string):
    return input_string.split("&lt;")[0]

plot_settings = {
    "error": {
        "title": "Exception occurences by type",
        "xlabel": "Exception type",
        "filepath": "exception_types.png",
        "color": "darkred"
    },
    "failure": {
        "title": "Failure occurences by type",
        "xlabel": "Failure type",
        "filepath": "failure_types.png",
        "color": "red"
    },
    "skip": {
        "title": "Skip occurences by reason",
        "xlabel": "Skip reason",
        "filepath": "skip_reasons.png",
        "color": "grey"
    }
}

plot_categories(pd.read_json("analytics_results/error.json"), 5, error_formatter, plot_settings["error"])
plot_categories(pd.read_json("analytics_results/fail.json"), 10, fail_formatter, plot_settings["failure"])
plot_categories(pd.read_json("analytics_results/skip.json"), 50, skip_formatter, plot_settings["skip"])