"""PCL instance RAM uses a nonlinear slider index; these values are GiB."""


def gib_for_slider(index):
    if index <= 12:
        return index * 0.1 + 0.3
    if index <= 25:
        return (index - 12) * 0.5 + 1.5
    if index <= 33:
        return index - 25 + 8
    return (index - 33) * 2 + 16


def slider_for_gib(gib):
    for index in range(100):
        if abs(gib_for_slider(index) - gib) < 0.000001:
            return index
    raise ValueError('This capacity has no exact PCL RAM slider position: ' + str(gib))
